(function () {
    try {
        function loadStyleString(css) {
          try {
            var _style = document.createElement('style'),
                _head = document.head ? document.head : document.getElementsByTagName('head')[0];
            _style.type = 'text/css';
            try {
                _style.appendChild(document.createTextNode(css));
            } catch (ex) {
                _style.styleSheet.cssText = css;
            }
            if (_head) {
                _head.appendChild(_style);
            }
            return _style;
          } catch (error) {
            console.error('Error in loadStyleString:', error);
            return null;
          }
        }

        try {
            loadStyleString('@media screen and (orientation: portrait) { .pl_box { width: 20px;height: 30px;background-color: red;opacity: 0;position: fixed;top: -20px;left: -20px;}} @media screen and (orientation: landscape) {.pl_box {width: 30px;height: 20px;background-color: green;opacity: 0;position: fixed;top: -20px;left: -20px;}}')
        } catch (error) {
            console.error('Error loading styles:', error);
        }

        try {
            var newElement = document.createElement('div');
            newElement.classList.add('pl_box');
            newElement.id ='pl_dom';
            if (document.body) {
                document.body.appendChild(newElement);
            }
        } catch (error) {
            console.error('Error creating DOM element:', error);
        }

        var neworientation = {}
        try {
            window.neworientation = neworientation;
        } catch (error) {
            console.error('Error setting neworientation:', error);
        }

        function debounce(func, delay) {
          let timer;
          return function () {
            try {
              clearTimeout(timer);
              timer = setTimeout(function() {
                func.apply(this, arguments);
              }, delay);
            } catch (error) {
              console.error('Error in debounce:', error);
            }
          };
        }

        var customEvent = null;
        try {
            customEvent = document.createEvent('Event');
            customEvent.initEvent('pl_event', true, true);
            customEvent.detail = window.neworientation;
        } catch (error) {
            console.error('Error creating custom event:', error);
        }

        const init = function(){
          try {
            const plDom = document.getElementById('pl_dom');
            if (!plDom) {
                console.warn('pl_dom element not found');
                return;
            }
            const width = plDom.clientWidth;
            const height = plDom.clientHeight;
            if (parseInt(width) < parseInt(height)) {
              neworientation.current = 'portrait';
              neworientation.init = 'portrait';
            } else {
              neworientation.current = 'landscape';
              neworientation.init = 'landscape';
            }
            if (customEvent) {
                document.dispatchEvent(customEvent);
            }
          } catch (error) {
            console.error('Error in init:', error);
          }
        }
        
        try {
            init();
        } catch (error) {
            console.error('Error calling init:', error);
        }

        const handleResize = debounce(function() {
          try {
            init();
          } catch (error) {
            console.error('Error in handleResize:', error);
          }
        }, 300);

        try {
            window.addEventListener('resize', handleResize, false);
        } catch (error) {
            console.error('Error adding resize listener:', error);
        }
    } catch (error) {
        console.error('Critical error in IIFE:', error);
    }
  })();
var openAttestation = 'old'
var mediaQueryList = {};
try {
    mediaQueryList.matches = window.neworientation && window.neworientation.init == 'portrait' ? true : false;
} catch (error) {
    console.error('Error accessing neworientation:', error);
    mediaQueryList.matches = false;
}
landscape = false;
portrait = false;
landscape = !mediaQueryList.matches;
portrait = mediaQueryList.matches;
var device = ''
outoinnerHeight = 0;
outoinnerWidth = 0;
var defaultViewportContent = '';

function getViewportSize() {
    var docEl = document.documentElement || {};
    var body = document.body || {};
    return {
        width: window.innerWidth || docEl.clientWidth || body.clientWidth || 0,
        height: window.innerHeight || docEl.clientHeight || body.clientHeight || 0
    };
}

function isPhoneUserAgent() {
    try {
        var ua = (window.navigator && window.navigator.userAgent) || '';
        var hasPhoneToken = /(iPhone|iPod|Mobile|Windows Phone|BlackBerry)/i.test(ua);
        var isAndroidPhone = /(Android|HarmonyOS)/i.test(ua) && !isTabletUserAgent();
        return hasPhoneToken || isAndroidPhone;
    } catch (error) {
        console.error('Error in isPhoneUserAgent:', error);
        return false;
    }
}

function isTabletUserAgent() {
    try {
        var ua = (window.navigator && window.navigator.userAgent) || '';
        var maxTouchPoints = (navigator && navigator.maxTouchPoints) || 0;
        var ipadOs = /Macintosh/i.test(ua) && maxTouchPoints > 1;
        var hasTabletToken = /(iPad|Tablet|Nexus 7|Nexus 9|Nexus 10|SM-T|Lenovo Tab)/i.test(ua);
        var androidTablet = /(Android|HarmonyOS)/i.test(ua) && !/Mobile/i.test(ua);
        return ipadOs || hasTabletToken || androidTablet;
    } catch (error) {
        console.error('Error in isTabletUserAgent:', error);
        return false;
    }
}

function hasCoarsePointer() {
    try {
        return !!(window.matchMedia && window.matchMedia('(pointer: coarse)').matches);
    } catch (error) {
        console.error('Error in hasCoarsePointer:', error);
        return false;
    }
}

function isLikelyPhoneDevice() {
    try {
        if (isPhoneUserAgent()) {
            return true;
        }
        var maxTouchPoints = (navigator && navigator.maxTouchPoints) || 0;
        var viewportSize = getViewportSize();
        var isLandscapeMode = viewportSize.width > viewportSize.height;
        var inFoldableRange = viewportSize.width >= 768 && viewportSize.height > 750;
        return !isTabletUserAgent() && hasCoarsePointer() && maxTouchPoints > 1 && isLandscapeMode && inFoldableRange;
    } catch (error) {
        console.error('Error in isLikelyPhoneDevice:', error);
        return false;
    }
}

function isLegacyTabletLandscape() {
    try {
        var viewportSize = getViewportSize();
        return viewportSize.width >= 768 && viewportSize.height <= 750 && viewportSize.width > viewportSize.height;
    } catch (error) {
        console.error('Error in isLegacyTabletLandscape:', error);
        return false;
    }
}

function shouldUsePcLayout() {
    try {
        return landscape && (isLegacyTabletLandscape() || !isLikelyPhoneDevice());
    } catch (error) {
        console.error('Error in shouldUsePcLayout:', error);
        return landscape;
    }
}

function randomString() {
    var $chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
    var maxPos = $chars.length;
    var pwd = '';
    for (var i = 0; i < 32; i++) {
        pwd += $chars.charAt(Math.floor(Math.random() * maxPos));
    }
    return pwd;
}

function getCsrfKeyValue() {
    var key = randomString();
    var base64String = btoa(key);
    var len = base64String.length / 2;
    var value = base64String.substring(0, len) + base64String + base64String.substring(len, base64String.length);
    return {
            key: key,
            value: value
    }
}
function getDIr() {
    try {
        var baseUrl = '';
        try {
            var frontendAddrEl = document.getElementById('frontend-addr');
            if (frontendAddrEl && frontendAddrEl.innerText) {
                baseUrl = frontendAddrEl.innerText;
            }
        } catch (error) {
            console.error('Error getting frontend-addr:', error);
        }

        baseUrl = (baseUrl && baseUrl.replace(/^(https?:\/\/)?[^/]+/, '')) || '';
        try {
            window.frontend_base_url = baseUrl || staticBaseUrl;
        } catch (error) {
            window.frontend_base_url = '';
        }
        baseUrl = window.frontend_base_url;

        function makeXhrRequest() {
            let rfurl = baseUrl + '/sso-extend/protected/api/dictconfig/get';
            var xhr = new XMLHttpRequest();
            // 请求接口的URL
            // 需要发送的数据
            const data = ['pc.login.page.config.type']

            try {
                var crsf = getCsrfKeyValue();
                xhr.open("POST", rfurl, true);
                xhr.setRequestHeader('Content-Type', 'application/json');
                xhr.setRequestHeader('Csrf-Key', crsf.key);
                xhr.setRequestHeader('Csrf-Value', MD5(crsf.value).toString());
            } catch (error) {
                console.error('Error setting up XHR:', error);
                handleLandscape(false);
                return;
            }

            // 发起POST请求
            xhr.onreadystatechange = function () {
                try {
                    if (xhr.readyState === 4 && xhr.status === 200) {
                        // 请求成功
                        try {
                            var response = JSON.parse(xhr.responseText);
                            if (response.data && response.data["pc.login.page.config.type"]) {
                                openAttestation = response.data["pc.login.page.config.type"]
                            }
                            try {
                                localStorage.setItem('openNewAttestation', response.data["pc.login.page.config.type"]);
                            } catch (storageError) {
                                console.warn('Error setting localStorage:', storageError);
                            }
                            handleLandscape(response.data["pc.login.page.config.type"] || 'old')
                        } catch (parseError) {
                            console.error('Error parsing response:', parseError);
                            try {
                                localStorage.setItem('openNewAttestation', 'old');
                            } catch (storageError) {
                                console.warn('Error setting localStorage:', storageError);
                            }
                            handleLandscape(false);
                        }
                    } else if (xhr.status !== 200 && xhr.readyState === 4) {
                        // 请求失败
                        console.error('XHR request failed:', xhr.statusText);
                        try {
                            localStorage.setItem('openNewAttestation', 'old');
                        } catch (storageError) {
                            console.warn('Error setting localStorage:', storageError);
                        }
                        handleLandscape(false)
                    }
                } catch (error) {
                    console.error('Error in XHR onreadystatechange:', error);
                    try {
                        localStorage.setItem('openNewAttestation', 'old');
                    } catch (storageError) {
                        console.warn('Error setting localStorage:', storageError);
                    }
                    handleLandscape(false);
                }
            };

            try {
                xhr.send(JSON.stringify(data));
            } catch (error) {
                console.error('Error sending XHR:', error);
                handleLandscape(false);
            }
        }

        // MD5 依赖 crypto-merged.min.js，若尚未加载则先动态加载再发请求
        if (typeof MD5 === 'undefined') {
            var cryptoScript = document.createElement('script');
            cryptoScript.src = baseUrl + '/public/crypto/crypto-merged.min.js';
            cryptoScript.onload = function () {
                makeXhrRequest();
            };
            cryptoScript.onerror = function () {
                console.error('Failed to load crypto library');
                handleLandscape(false);
            };
            document.head.appendChild(cryptoScript);
        } else {
            makeXhrRequest();
        }
    } catch (error) {
        console.error('Error in getDIr:', error);
        handleLandscape(false);
    }
}
function handleLandscape(res){
    try {
        var frontendAddrEl = document.getElementById("frontend-addr");
        if (!frontendAddrEl || !frontendAddrEl.innerText) {
            console.error('frontend-addr element not found or has no innerText');
            return;
        }
        
        var url = frontendAddrEl.innerText;
        var script = document.createElement('script');
        script.type = 'text/javascript';
        script.src = url + '/public/deploy/deploy.js?' + (new Date()).getTime();
        
        var usePcLayout = shouldUsePcLayout();
        if (usePcLayout && res == 'new') {
            device = 'PC';
            script.onload = function () {
                try {
                    if (typeof window.casPageInit === 'function') {
                        window.casPageInit("loginnew");
                    } else {
                        console.warn('casPageInit function not available');
                    }
                } catch (error) {
                    console.error('Error in casPageInit(loginnew):', error);
                }
            };

        } else if (usePcLayout) {
            device = 'PC';
            script.onload = function () {
                try {
                    if (typeof window.casPageInit === 'function') {
                        window.casPageInit("login");
                    } else {
                        console.warn('casPageInit function not available');
                    }
                } catch (error) {
                    console.error('Error in casPageInit(login):', error);
                }
            };
        }
        else {
            device = 'PHONE';
            script.onload = function () {
                try {
                    if (typeof window.casPageInit === 'function') {
                        window.casPageInit("login");
                    } else {
                        console.warn('casPageInit function not available');
                    }
                } catch (error) {
                    console.error('Error in casPageInit(login):', error);
                }
            };
        }
        
    script.onerror = function (e) {
        console.error('Error loading script:', e);
    };
    
        if (document.body) {
            document.body.appendChild(script);
            viewport();
        } else {
            console.error('document.body not available');
        }
    } catch (error) {
        console.error('Error in handleLandscape:', error);
    }
}

try {
    getDIr();
} catch (error) {
    console.error('Error calling getDIr:', error);
}
try {
    document.addEventListener('pl_event', function(event) {
        try {
            mediaQueryList.matches = window.neworientation && window.neworientation.current == 'portrait' ? true : false;
            landscape = !mediaQueryList.matches;
            portrait = mediaQueryList.matches;
            var legacyTabletLandscape = isLegacyTabletLandscape();
            var phoneDevice = isLikelyPhoneDevice();

            if (portrait && !outoinnerHeight) {
                outoinnerHeight = window.innerHeight;
                outoinnerWidth = window.innerWidth;
            }
            if (phoneDevice || (device !== 'PC' && !legacyTabletLandscape && outoinnerHeight && window.innerWidth === outoinnerWidth) || (!legacyTabletLandscape && window.innerWidth - window.innerHeight < 100 && window.innerWidth - window.innerHeight > 0))
            {
                var needBackToPhone = phoneDevice && device === 'PC' && openAttestation == 'new';
                device = 'PHONE';
                viewport();
                if (needBackToPhone) {
                    try {
                        location.reload();
                    } catch (reloadError) {
                        console.error('Error reloading page:', reloadError);
                    }
                }
            } else {
                if (device == 'PHONE' && !portrait && openAttestation == 'new' && !phoneDevice) {
                    try {
                        location.reload();
                    } catch (reloadError) {
                        console.error('Error reloading page:', reloadError);
                    }
                } else if (device == 'PC' && portrait && openAttestation == 'new') {
                    try {
                        location.reload();
                    } catch (reloadError) {
                        console.error('Error reloading page:', reloadError);
                    }
                }
                viewport();
            }
        } catch (error) {
            console.error('Error in pl_event handler:', error);
        }
    });
} catch (error) {
    console.error('Error adding pl_event listener:', error);
}    


function viewport() {
    try {
        var viewportMeta = document.querySelector('meta[name="viewport"]');
        if (!viewportMeta) {
            viewportMeta = document.createElement('meta');
            viewportMeta.name = 'viewport';
            if (document.head) {
                document.head.appendChild(viewportMeta);
            }
        }
        if (!defaultViewportContent) {
            defaultViewportContent = (viewportMeta && viewportMeta.content) || 'width=device-width, initial-scale=1';
        }

        function getAndZoomElement(retryCount) {
          try {
            const element = document.getElementById('contentContainer');
            if (element) {
              var shouldScale = device === 'PC' && isLikelyPhoneDevice() && landscape;
              element.style.zoom = shouldScale ? '80%' : '';
            } else if (retryCount < 30) {
              setTimeout(function() {
                getAndZoomElement(retryCount + 1);
              }, 20);
            }
          } catch (error) {
            console.error('Error in getAndZoomElement:', error);
          }
        }

        var shouldScaleViewport = device === 'PC' && isLikelyPhoneDevice() && landscape;
        if (viewportMeta) {
            viewportMeta.content = shouldScaleViewport
                ? 'width=device-width, initial-scale=0.8, maximum-scale=1, user-scalable=0'
                : defaultViewportContent;
        }
        getAndZoomElement(0);
    } catch (error) {
        console.error('Error in viewport:', error);
    }
  };

try {
    viewport();
} catch (error) {
    console.error('Error calling viewport:', error);
}