package com.buct.course.ui.screen

import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.buct.course.ui.system.SystemPrimaryButton
import kotlinx.coroutines.launch

/**
 * 统一认证网页快捷登录浮层（金智 linkid 短信验证码 / 指纹登录）。
 *
 * 北化统一认证的短信 OTP 与指纹（WebAuthn）能力只在浏览器环境可用，
 * 后端接口对非浏览器流量做了网关拦截，因此用 App 内嵌 WebView 打开认证页，
 * 用户在浮层内完成认证；一旦检测到教务会话 Cookie，自动提取并回调，无需再跳转。
 */
@Composable
fun PortalLoginWebDialog(
    onCookie: (String) -> Unit,
    onDismiss: () -> Unit,
    smsUsername: String? = null,
    passkeyUsername: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    var pageUrl by remember { mutableStateOf("") }
    var loadingProgress by remember { mutableFloatStateOf(0f) }
    var done by remember { mutableStateOf(false) }
    var firstLoad by remember { mutableStateOf(true) }
    var autoInjected by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var nativeUser by remember { mutableStateOf(smsUsername ?: passkeyUsername ?: "") }
    var nativePass by remember { mutableStateOf("") }
    var nativeCode by remember { mutableStateOf("") }
    // 短信模式需要验证码输入；密码模式（通行密钥未注册绑定）需要密码输入
    val smsMode = smsUsername != null && !smsUsername.isBlank()

    val cookieUrl = "https://jwglxt.buct.edu.cn/"
    val startUrl = "https://sidportal.buct.edu.cn/login?service=" +
        android.net.Uri.encode("https://jwglxt.buct.edu.cn/sso/jziotlogin")

    fun extractCookie() {
        if (done) return
        val cookie = CookieManager.getInstance().getCookie(cookieUrl).orEmpty().trim()
        if (cookie.isBlank()) return
        done = true
        keyboardController?.hide()
        scope.launch {
            onCookie(cookie)
        }
    }

    Dialog(
        onDismissRequest = {
            if (!done) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 顶栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { if (!done) onDismiss() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "关闭", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "统一认证登录",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "北京化工大学 · 短信验证码 / 指纹",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { if (!done) onDismiss() }) {
                        Icon(Icons.Default.Close, contentDescription = "关闭", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
                if (loadingProgress < 1f) {
                    LinearProgressIndicator(
                        progress = { loadingProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                // 原生输入条：网页输入框受系统 WebView 输入法 bug 影响（倒序/无法输入），
                // 改用 App 原生输入框填好后一键注入网页提交——任何设备、任何 WebView 版本都正常
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (smsMode) "原生短信验证码（避开网页输入法问题）" else "原生密码登录（避开网页输入法问题）",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = nativeUser,
                                onValueChange = { nativeUser = it },
                                modifier = Modifier.weight(if (smsMode) 0.8f else 1f),
                                placeholder = { Text("学号") },
                                singleLine = true
                            )
                            if (smsMode) {
                                OutlinedTextField(
                                    value = nativeCode,
                                    onValueChange = { nativeCode = it },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text("短信验证码") },
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                    singleLine = true
                                )
                            } else {
                                OutlinedTextField(
                                    value = nativePass,
                                    onValueChange = { nativePass = it },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text("密码") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true
                                )
                            }
                            SystemPrimaryButton(
                                text = if (smsMode) "填入并提交" else "填入并登录",
                                onClick = {
                                    val wv = webViewRef
                                    if (wv != null && nativeUser.isNotBlank() &&
                                        (if (smsMode) nativeCode.isNotBlank() else nativePass.isNotBlank())
                                    ) {
                                        val js = if (smsMode) {
                                            buildSmsCodeInjectJs(nativeUser, nativeCode)
                                        } else {
                                            buildNativeLoginInjectJs(nativeUser, nativePass)
                                        }
                                        wv.evaluateJavascript(js, null)
                                        keyboardController?.hide()
                                    }
                                },
                                modifier = Modifier.height(50.dp)
                            )
                        }
                        if (smsMode) {
                            Text(
                                text = "先在网页点「获取验证码」，收到短信后在左侧输入验证码，点「填入并提交」",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "学号已自动填入，输密码后点「填入并登录」（通行密钥未注册时用此方式绑定）",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webViewRef = this
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            // 关键：使用 Chrome UA。金智短信/OTP 服务对 WebView 默认 UA 不响应（请求挂起一直转圈），
                            // 密码登录链路用 Chrome UA 成功过，这里保持一致
                            settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                            // 强制 LTR 文本方向，修复中文输入法下 WebView 输入倒序
                            textDirection = android.view.View.TEXT_DIRECTION_LTR
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            webViewClient = object : WebViewClient() {
                                // 诊断日志：打印短信/OTP 相关请求，定位「一直转圈」是没发出还是被拦截
                                override fun shouldInterceptRequest(
                                    view: WebView,
                                    request: WebResourceRequest
                                ): android.webkit.WebResourceResponse? {
                                    val u = request.url.toString()
                                    if (u.contains("otpAuthn") || u.contains("sms") || u.contains("protected") || u.contains("checkUser") || u.contains("getSessionCode")) {
                                        android.util.Log.e("PortalWeb", "REQ ${request.method} $u")
                                    }
                                    return null
                                }
                                override fun onReceivedHttpError(
                                    view: WebView,
                                    request: WebResourceRequest,
                                    errorResponse: android.webkit.WebResourceResponse
                                ) {
                                    val u = request.url.toString()
                                    if (u.contains("otpAuthn") || u.contains("sms") || u.contains("protected")) {
                                        android.util.Log.e("PortalWeb", "HTTPERR ${errorResponse.statusCode} $u")
                                    }
                                }
                                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                    val u = url.orEmpty()
                                    pageUrl = u
                                    loadingProgress = 0f
                                    // 不在此处自动提取 Cookie，避免跳转瞬间 Cookie 未写全导致误判完成
                                }
                                override fun onPageFinished(view: WebView, url: String?) {
                                    loadingProgress = 1f
                                    pageUrl = url.orEmpty()
                                    // 诊断钩子：包装 fetch 抓取短信接口的请求参数与响应（仅当 URL 含 sms/generate/Captcha）
                                    val fetchHookJs = """
                                        (function(){
                                          if(window.__smsHooked)return;window.__smsHooked=true;
                                          var of=window.fetch;
                                          window.fetch=function(u,o){
                                            var url=(typeof u==='string')?u:(u&&u.url)?u.url:'';
                                            var body=(o&&o.body)?String(o.body):'';
                                            var opts=o||{};
                                            var method=(opts.method||'GET');
                                            if(url.indexOf('sms')>-1||url.indexOf('generate')>-1||url.indexOf('Captcha')>-1||url.indexOf('otpAuthn')>-1){
                                              console.log('[SMSREQ] '+method+' '+url+(body?' BODY:'+body:''));
                                              return of.apply(this,arguments).then(function(r){
                                                try{r.clone().text().then(function(t){console.log('[SMSRESP] '+r.status+' '+url+' RESP:'+t.substring(0,600));});}catch(e){}
                                                return r;
                                              });
                                            }
                                            return of.apply(this,arguments);
                                          };
                                          // XHR 钩子（金智用 axios，底层走 XMLHttpRequest）
                                          var _oxhrOpen=XMLHttpRequest.prototype.open;
                                          XMLHttpRequest.prototype.open=function(m,u){
                                            this.__m=m;this.__u=u;return _oxhrOpen.apply(this,arguments);
                                          };
                                          var _oxhrSetHeader=XMLHttpRequest.prototype.setRequestHeader;
                                          XMLHttpRequest.prototype.setRequestHeader=function(k,v){
                                            if(!this.__hdrs)this.__hdrs={};
                                            this.__hdrs[k]=v;
                                            return _oxhrSetHeader.apply(this,arguments);
                                          };
                                          var _oxhrSend=XMLHttpRequest.prototype.send;
                                          XMLHttpRequest.prototype.send=function(body){
                                            var self=this;
                                            if(this.__u&&(this.__u.indexOf('sms')>-1||this.__u.indexOf('generate')>-1||this.__u.indexOf('Captcha')>-1||this.__u.indexOf('otpAuthn')>-1)){
                                              var hdrs=this.__hdrs?JSON.stringify(this.__hdrs):'';
                                              console.log('[SMSREQ] '+self.__m+' '+self.__u+' BODY:'+(body?body:'')+' HDRS:'+hdrs);
                                              this.addEventListener('load',function(){
                                                try{console.log('[SMSRESP] '+self.status+' '+self.__u+' RESP:'+self.responseText.substring(0,600));}catch(e){}
                                              });
                                              this.addEventListener('error',function(){
                                                try{console.log('[SMSRESP] ERR '+self.__u);}catch(e){}
                                              });
                                            }
                                            return _oxhrSend.apply(this,arguments);
                                          };
                                        })();
                                    """.trimIndent()
                                    view.evaluateJavascript(fetchHookJs, null)
                                    view.postDelayed({ view.evaluateJavascript(fetchHookJs, null) }, 1000)
                                    // 注：网页输入倒序是 Android System WebView 组件版本 bug（Chrome 浏览器正常），
                                    // 已移除 JS 方向注入以避免干扰输入；解决方案是升级系统 WebView 或启用 Chrome 作为 WebView 实现
                                    // 短信模式：自动切「短信验证码」tab 并填入学号
                                    if (smsUsername != null && !smsUsername.isBlank() && !autoInjected && url.orEmpty().contains("sidportal.buct.edu.cn")) {
                                        autoInjected = true
                                        val js = buildSmsAutoFillJs(smsUsername)
                                        view.postDelayed({ view.evaluateJavascript(js, null) }, 800)
                                    }
                                    // 通行密钥模式：自动切「通行密钥认证」tab、填学号并触发登录（网页自行判断是否已注册，已注册直接调指纹）
                                    if (passkeyUsername != null && !passkeyUsername.isBlank() && !autoInjected && url.orEmpty().contains("sidportal.buct.edu.cn")) {
                                        autoInjected = true
                                        val js = buildPasskeyAutoFillJs(passkeyUsername)
                                        view.postDelayed({ view.evaluateJavascript(js, null) }, 800)
                                    }
                                }
                            }
                            webChromeClient = object : android.webkit.WebChromeClient() {
                                override fun onProgressChanged(view: WebView, newProgress: Int) {
                                    loadingProgress = newProgress / 100f
                                }
                                // 把注入脚本 console 日志转发到 logcat，用于抓取短信接口请求参数/响应
                                override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                                    val msg = message.message()
                                    if (msg.startsWith("[SMS") || msg.startsWith("[SMSREQ]") || msg.startsWith("[SMSRESP]")) {
                                        android.util.Log.e("PortalWeb", msg)
                                    }
                                    return super.onConsoleMessage(message)
                                }
                            }
                        }
                    },
                    // 每次打开都先清空 WebView 会话（等清除完成再加载），确保走真实的短信/指纹认证流程
                    update = { view ->
                        if (firstLoad) {
                            firstLoad = false
                            CookieManager.getInstance().removeAllCookies {
                                view.loadUrl(startUrl)
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
                // 底部操作栏
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "在页面上完成短信验证码或指纹认证后，点「完成登录」自动返回",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SystemPrimaryButton(
                        text = "完成登录",
                        onClick = { extractCookie() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/** 原生密码注入：切「用户名密码」tab → 用原生 setter 填学号/密码（绕开框架 value 监听）→ 点登录（页面自行加密/校验） */
private fun buildNativeLoginInjectJs(username: String, password: String): String {
    fun jsStr(s: String): String = "'" + s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n") + "'"
    val u = jsStr(username)
    val p = jsStr(password)
    return """
        (function(){
          function setVal(el,v){
            try{
              var proto=HTMLInputElement.prototype;
              Object.getOwnPropertyDescriptor(proto,'value').set.call(el,v);
            }catch(e){el.value=v;}
            el.dispatchEvent(new Event('input',{bubbles:true}));
            el.dispatchEvent(new Event('change',{bubbles:true}));
            el.dispatchEvent(new Event('blur',{bubbles:true}));
          }
          function findInFrames(sel){
            var el=document.querySelector(sel);
            if(el)return el;
            var frames=document.querySelectorAll('iframe');
            for(var i=0;i<frames.length;i++){
              try{
                var d=frames[i].contentDocument;
                if(d){el=d.querySelector(sel);if(el)return el;}
              }catch(err){}
            }
            return null;
          }
          function clickByText(t){
            var els=document.querySelectorAll('div,span,a,li,button,label,p');
            for(var i=0;i<els.length;i++){
              var x=els[i];
              if(x.children.length===0 && x.textContent && x.textContent.trim()===t){
                x.click(); return true;
              }
            }
            var frames=document.querySelectorAll('iframe');
            for(var j=0;j<frames.length;j++){
              try{
                var d=frames[j].contentDocument;
                if(d){
                  var fe=d.querySelectorAll('div,span,a,li,button,label,p');
                  for(var k=0;k<fe.length;k++){
                    if(fe[k].children.length===0 && fe[k].textContent && fe[k].textContent.trim()===t){fe[k].click();return true;}
                  }
                }
              }catch(err){}
            }
            return false;
          }
          clickByText('用户名密码');
          setTimeout(function(){
            var u=findInFrames('input[name="username"],input[autocomplete="username"],input[placeholder*="学号"],input[placeholder*="用户名"],input[placeholder*="工号"],input[placeholder*="账号"]');
            if(u){setVal(u,$u);}
            var p=findInFrames('input[type="password"],input[name="password"]');
            if(p){setVal(p,$p);}
            setTimeout(function(){
              var btn=findInFrames('button[type="submit"],button[class*="login"],button[class*="submit"]');
              if(btn){btn.click();return;}
              var els=document.querySelectorAll('button,div,span,a');
              for(var i=0;i<els.length;i++){
                var x=els[i];
                if(x.children.length===0&&x.textContent&&x.textContent.trim()==='登录'){x.click();break;}
              }
            },400);
          },500);
        })();
    """.trimIndent()
}

/** 短信验证码注入：填学号（若空）+ 填验证码 → 点「登录/提交」完成短信登录 */
private fun buildSmsCodeInjectJs(username: String, code: String): String {
    fun jsStr(s: String): String = "'" + s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n") + "'"
    val u = jsStr(username)
    val c = jsStr(code)
    return """
        (function(){
          function setVal(el,v){
            try{
              var proto=HTMLInputElement.prototype;
              Object.getOwnPropertyDescriptor(proto,'value').set.call(el,v);
            }catch(e){el.value=v;}
            el.dispatchEvent(new Event('input',{bubbles:true}));
            el.dispatchEvent(new Event('change',{bubbles:true}));
            el.dispatchEvent(new Event('blur',{bubbles:true}));
          }
          function findInFrames(sel){
            var el=document.querySelector(sel);
            if(el)return el;
            var frames=document.querySelectorAll('iframe');
            for(var i=0;i<frames.length;i++){
              try{
                var d=frames[i].contentDocument;
                if(d){el=d.querySelector(sel);if(el)return el;}
              }catch(err){}
            }
            return null;
          }
          function clickByText(t){
            var els=document.querySelectorAll('div,span,a,li,button,label,p');
            for(var i=0;i<els.length;i++){
              var x=els[i];
              if(x.children.length===0 && x.textContent && x.textContent.trim()===t){
                x.click(); return true;
              }
            }
            var frames=document.querySelectorAll('iframe');
            for(var j=0;j<frames.length;j++){
              try{
                var d=frames[j].contentDocument;
                if(d){
                  var fe=d.querySelectorAll('div,span,a,li,button,label,p');
                  for(var k=0;k<fe.length;k++){
                    if(fe[k].children.length===0 && fe[k].textContent && fe[k].textContent.trim()===t){fe[k].click();return true;}
                  }
                }
              }catch(err){}
            }
            return false;
          }
          var u=findInFrames('input[placeholder*="学号"],input[placeholder*="手机"],input[placeholder*="账号"],input[placeholder*="用户名"]');
          if(u&&u.value===''){setVal(u,$u);}
          var c=findInFrames('input[placeholder*="验证码"],input[name*="code"],input[name*="Code"],input[autocomplete="one-time-code"],input[maxlength="6"]');
          if(c){setVal(c,$c);}
          setTimeout(function(){
            var btn=findInFrames('button[type="submit"],button[class*="login"],button[class*="submit"]');
            if(btn){btn.click();return;}
            clickByText('登录');clickByText('确认');clickByText('提交');
          },400);
        })();
    """.trimIndent()
}

/** 通行密钥模式自动注入：切「通行密钥认证」tab、填学号、点登录（触发 WebAuthn，网页自行判断是否已注册） */
private fun buildPasskeyAutoFillJs(username: String): String {
    val safeUser = username.replace("\\", "\\\\").replace("'", "\\'")
    return """
        (function(){
          function clickByText(t){
            var els=document.querySelectorAll('div,span,a,li,button,label,p');
            for(var i=0;i<els.length;i++){
              var x=els[i];
              if(x.children.length===0 && x.textContent && x.textContent.trim()===t){
                x.click(); return true;
              }
            }
            return false;
          }
          clickByText('通行密钥认证');
          setTimeout(function(){
            var inputs=document.querySelectorAll('input');
            for(var i=0;i<inputs.length;i++){
              var inp=inputs[i];
              var ph=(inp.placeholder||'');
              if(ph.indexOf('学号')>=0||ph.indexOf('手机')>=0||ph.indexOf('账号')>=0||ph.indexOf('用户名')>=0){
                inp.value='$safeUser';
                inp.dispatchEvent(new Event('input',{bubbles:true}));
                inp.dispatchEvent(new Event('change',{bubbles:true}));
                break;
              }
            }
            setTimeout(function(){ clickByText('登录'); }, 600);
          }, 600);
        })();
    """.trimIndent()
}

/** 短信模式自动注入：切「短信验证码」tab 并填入学号 */
private fun buildSmsAutoFillJs(username: String): String {
    val safeUser = username.replace("\\", "\\\\").replace("'", "\\'")
    return """
        (function(){
          function clickByText(t){
            var els=document.querySelectorAll('div,span,a,li,button,label,p');
            for(var i=0;i<els.length;i++){
              var x=els[i];
              if(x.children.length===0 && x.textContent && x.textContent.trim()===t){
                x.click(); return true;
              }
            }
            return false;
          }
          clickByText('短信验证码');
          setTimeout(function(){
            var inputs=document.querySelectorAll('input');
            for(var i=0;i<inputs.length;i++){
              var inp=inputs[i];
              var ph=(inp.placeholder||'');
              if(ph.indexOf('学号')>=0||ph.indexOf('手机')>=0||ph.indexOf('账号')>=0||ph.indexOf('用户名')>=0){
                inp.value='$safeUser';
                inp.dispatchEvent(new Event('input',{bubbles:true}));
                inp.dispatchEvent(new Event('change',{bubbles:true}));
                break;
              }
            }
          },600);
        })();
    """.trimIndent()
}
