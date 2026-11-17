/* 北京化工大学 正方教务(jwglxt) 课表适配插件
 * 基于 API v1 extension (extends: builtin.zf)
 * 实测接口(2026-09):
 *   1. GET  /jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151 → 学期下拉
 *   2. POST /jwglxt/kbcx/xskbcx_cxXsKb.html?gnmkdm=N2151
 *      form: xnm=年份, xqm=学期(3=第一学期, 12=第二学期, 16=小学期)
 *      → JSON kbList[]: kcmc课程名/xm教师/cdmc教室/xqj星期/jcs节次/jcor连上节次/zcd周次
 */
var __schoolModule = (function () {
  var SCHEDULE_GNMKDM = "N2151";
  var TERMS_PATH = "/kbcx/xskbcx_cxXskbcxIndex.html";
  var SCHEDULE_PATH = "/kbcx/xskbcx_cxXsKb.html";
  var MAX_WEEKS = 25;
  var FALLBACK_WEEKS = 20;
  var TERM_PATTERN = /(\d{4})\D+(\d{4})\D+([123])(?:\D|$)/;
  var YEAR_FIELDS = ["xnm", "xnd", "ddlXN", "xn"];
  var SEMESTER_FIELDS = ["xqm", "xqd", "ddlXQ", "xq"];

  function api(context, path) {
    return context.baseUrl.replace(/\/+$/, "") + path;
  }
  function attr(node, name) {
    var value = node.attributes[name];
    return typeof value === "string" ? value : "";
  }
  function hasAttr(node, name) {
    var value = node.attributes[name];
    if (typeof value !== "string") return false;
    return value !== "" || Object.prototype.hasOwnProperty.call(node.attributes, name);
  }
  function isRecord(value) {
    return typeof value === "object" && value !== null && !Array.isArray(value);
  }
  function field(row, keys) {
    for (var i = 0; i < keys.length; i++) {
      var value = row[keys[i]];
      if (typeof value === "string") {
        var text = value.trim();
        if (text !== "") return text;
      } else if (typeof value === "number" && Number.isFinite(value)) {
        return String(value);
      }
    }
    return "";
  }
  function intField(row, keys) {
    for (var i = 0; i < keys.length; i++) {
      var value = row[keys[i]];
      var number = typeof value === "number" ? value : (typeof value === "string" && value.trim() !== "" ? Number(value.trim()) : Number.NaN);
      if (Number.isFinite(number)) return Math.trunc(number);
    }
    return null;
  }
  function arrayField(root, keys) {
    if (Array.isArray(root)) return root.filter(isRecord);
    if (!isRecord(root)) return [];
    for (var i = 0; i < keys.length; i++) {
      var value = root[keys[i]];
      if (Array.isArray(value)) return value.filter(isRecord);
    }
    if (isRecord(root.data)) return arrayField(root.data, keys);
    return [];
  }
  function rowsOf(body, keys) {
    var text = body.trim();
    if (text === "" || (text.charAt(0) !== "{" && text.charAt(0) !== "[")) return [];
    var root;
    try { root = JSON.parse(text); } catch (e) { return []; }
    return arrayField(root, keys);
  }
  function isLoginPage(body) {
    if (/<input[^>]*(?:type\s*=\s*["']?password|name\s*=\s*["']?(?:mm|TextBox2))/i.test(body)) return true;
    if (/location(?:\.href)?\s*=\s*['"][^'"]*(?:login_slogin|default2\.aspx|LoginToXk)/i.test(body)) return true;
    return /请先登录|登录超时|会话失效|notLogin/.test(body);
  }
  function parseTermId(value) {
    var match = TERM_PATTERN.exec(value);
    if (!match) return null;
    var year = Number(match[1]);
    return year + "-" + (year + 1) + "-" + match[3];
  }
  // 北化校历对齐：第一学期 = 当年8月最后一个周一，第二学期 = 次年2月最后一个周一，小学期留空（沿用 App 设置）。
  function lastMondayOfMonth(year, month) {
    var last = new Date(year, month, 0); // month: 1-12，0 表示上月最后一天
    var day = last.getDay(); // 0=周日 ... 6=周六
    var back = (day - 1 + 7) % 7;
    last.setDate(last.getDate() - back);
    return last;
  }
  function formatDate(d) {
    var m = d.getMonth() + 1;
    var day = d.getDate();
    return d.getFullYear() + "-" + (m < 10 ? "0" : "") + m + "-" + (day < 10 ? "0" : "") + day;
  }
  function termStartDate(year, semester) {
    if (semester === 1) return formatDate(lastMondayOfMonth(year, 8));
    if (semester === 2) return formatDate(lastMondayOfMonth(year + 1, 2));
    return ""; // 小学期
  }
  function toTerm(id) {
    var parts = id.split("-");
    var year = Number(parts[0]);
    var semester = Number(parts[2]);
    return { id: id, name: year + "-" + (year + 1) + "学年 第" + semester + "学期", year: year, semester: semester, order: year * 10 + semester, startDate: termStartDate(year, semester) };
  }
  function xqmToSemester(value) {
    if (value === "3" || value === "1") return 1;
    if (value === "12" || value === "2") return 2;
    if (value === "16") return 3;
    var number = Number(value);
    return Number.isFinite(number) && number >= 1 && number <= 3 ? number : 0;
  }
  function termsFromSelects(body, sdk) {
    var years = [];
    var semesters = [];
    var selectedYear = 0;
    var selectedSemester = 0;
    var selects = sdk.html.select(body, "select[name]");
    for (var s = 0; s < selects.length; s++) {
      var select = selects[s];
      var name = attr(select, "name").split("$").pop() || "";
      var options = sdk.html.select(select.html, "option");
      for (var o = 0; o < options.length; o++) {
        var option = options[o];
        if (hasAttr(option, "disabled")) continue;
        var value = attr(option, "value").trim();
        var marked = hasAttr(option, "selected");
        if (YEAR_FIELDS.indexOf(name) >= 0) {
          var year = Number(value.slice(0, 4));
          if (year >= 1900 && year <= 2300) {
            if (years.indexOf(year) < 0) years.push(year);
            if (marked && selectedYear === 0) selectedYear = year;
          }
        } else if (SEMESTER_FIELDS.indexOf(name) >= 0) {
          var semester = name === "xqm" ? xqmToSemester(value) : Number(value);
          if (semester >= 1 && semester <= 3) {
            if (semesters.indexOf(semester) < 0) semesters.push(semester);
            if (marked && selectedSemester === 0) selectedSemester = semester;
          }
        }
      }
    }
    var terms = [];
    for (var y = 0; y < years.length; y++) {
      for (var m = 0; m < semesters.length; m++) {
        terms.push(toTerm(years[y] + "-" + (years[y] + 1) + "-" + semesters[m]));
      }
    }
    var currentId = selectedYear > 0 && selectedSemester > 0 ? selectedYear + "-" + (selectedYear + 1) + "-" + selectedSemester : "";
    return { terms: terms, currentId: currentId };
  }
  function calendarTermId() {
    try {
      var now = new Date();
      var month = now.getMonth() + 1;
      var year = month >= 9 ? now.getFullYear() : now.getFullYear() - 1;
      var semester = month >= 9 || month <= 2 ? 1 : 2;
      return year + "-" + (year + 1) + "-" + semester;
    } catch (e) { return ""; }
  }
  async function readTerms(_args, context, sdk) {
    var response = await sdk.http({
      url: api(context, TERMS_PATH) + "?gnmkdm=" + SCHEDULE_GNMKDM,
      method: "GET",
      purpose: "query",
      charset: "UTF-8"
    });
    if (isLoginPage(response.body)) {
      return { ok: false, error: { code: "SESSION_EXPIRED", message: "登录已失效，请重新登录后再查询" } };
    }
    if (response.status < 200 || response.status > 299) {
      return { ok: false, error: { code: "PAGE_CHANGED", message: "学校课表页面暂不可用，请稍后重试" } };
    }
    var terms = [];
    var selectedId = "";
    // 尝试通用解析（value/text 形如 2026-2027-1）
    var options = sdk.html.select(response.body, "select option");
    for (var i = 0; i < options.length; i++) {
      if (hasAttr(options[i], "disabled")) continue;
      var id = parseTermId(attr(options[i], "value")) || parseTermId(options[i].text);
      if (!id) continue;
      var exists = false;
      for (var t = 0; t < terms.length; t++) if (terms[t].id === id) exists = true;
      if (!exists) terms.push(toTerm(id));
      if (selectedId === "" && hasAttr(options[i], "selected")) selectedId = id;
    }
    // 北化 xnm+xqm 双下拉结构：回退解析
    if (terms.length === 0) {
      var fallback = termsFromSelects(response.body, sdk);
      for (var f = 0; f < fallback.terms.length; f++) {
        var fallbackTerm = fallback.terms[f];
        var exists2 = false;
        for (var t2 = 0; t2 < terms.length; t2++) if (terms[t2].id === fallbackTerm.id) exists2 = true;
        if (!exists2) terms.push(fallbackTerm);
      }
      if (selectedId === "") selectedId = fallback.currentId;
    }
    if (terms.length === 0) {
      return { ok: false, error: { code: "PAGE_CHANGED", message: "学校课表页面没有返回可识别的学期" } };
    }
    terms.sort(function (a, b) { return (b.order || 0) - (a.order || 0); });
    var currentId = selectedId;
    var currentExists = false;
    for (var c = 0; c < terms.length; c++) if (terms[c].id === currentId) currentExists = true;
    if (!currentExists) currentId = calendarTermId();
    currentExists = false;
    for (var c2 = 0; c2 < terms.length; c2++) if (terms[c2].id === currentId) currentExists = true;
    if (!currentExists) currentId = terms[0].id;
    return { ok: true, data: { items: terms, currentId: currentId } };
  }
  // 解析节次："3-4节" / "3-4" / "第3-4节"；北化优先 jcor 连上节次，保证合并格覆盖全部节次
  function parsePeriods(value) {
    if (value === "") return null;
    var clean = value.replace(/[第节\s]/g, "").replace(/[,~—–至]/g, "-");
    var parts = clean.split("-").filter(function (p) { return p !== ""; });
    if (parts.length === 0) return null;
    var first = Number(parts[0]);
    var last = Number(parts[parts.length - 1]);
    if (!Number.isFinite(first) || !Number.isFinite(last)) return null;
    var start = Math.min(first, last);
    var end = Math.max(first, last);
    if (start < 1 || end < start || end > 30) return null;
    return [start, end];
  }
  // 解析周次："1-5周" / "6-7周" / "1-2周,4-11周" / "3周" / "1-16周(单)"
  function parseWeeks(value, maxWeeks) {
    if (value.trim() === "") return [];
    var parts = value.replace(/\s+/g, "").split(/[,、;]+/);
    var weeks = {};
    for (var i = 0; i < parts.length; i++) {
      var part = parts[i];
      if (part === "") return [];
      var odd = part.indexOf("单") >= 0;
      var even = part.indexOf("双") >= 0;
      if (odd && even) return [];
      var match = /^(\d{1,2})(?:[-~—–至](\d{1,2}))?$/.exec(part.replace(/[第周单双()（）]/g, ""));
      if (!match) return [];
      var first = Number(match[1]);
      var last = match[2] === undefined ? first : Number(match[2]);
      if (first < 1 || first > maxWeeks || last < first || last > maxWeeks) return [];
      for (var week = first; week <= last; week++) {
        if ((!odd || week % 2 === 1) && (!even || week % 2 === 0)) weeks[week] = true;
      }
    }
    return Object.keys(weeks).map(Number).sort(function (a, b) { return a - b; });
  }
  function entryId(seed) {
    var forward = 2166136261;
    var backward = 16777619;
    for (var index = 0; index < seed.length; index++) {
      forward = ((forward ^ seed.charCodeAt(index)) * 16777619) >>> 0;
      backward = ((backward + seed.charCodeAt(index) * (index + 7) ^ (forward >>> 3))) >>> 0;
    }
    return "buct-kb-" + forward.toString(16).padStart(8, "0") + backward.toString(16).padStart(8, "0");
  }
  async function readSchedule(args, context, sdk) {
    var parts = (args.termId || "").split("-");
    var year = Number(parts[0]);
    var semester = Number(parts[2]);
    if (!Number.isFinite(year) || year < 1900 || (semester !== 1 && semester !== 2 && semester !== 3)) {
      return { ok: false, error: { code: "VALIDATION_FAILED", message: "学期参数无法识别，请重新选择学期" } };
    }
    var xqm = semester === 1 ? "3" : semester === 2 ? "12" : "16";
    var response = await sdk.http({
      url: api(context, SCHEDULE_PATH) + "?gnmkdm=" + SCHEDULE_GNMKDM,
      method: "POST",
      purpose: "query",
      charset: "UTF-8",
      headers: { "X-Requested-With": "XMLHttpRequest" },
      form: { xnm: String(year), xqm: xqm }
    });
    if (isLoginPage(response.body)) {
      return { ok: false, error: { code: "SESSION_EXPIRED", message: "登录已失效，请重新登录后再查询" } };
    }
    if (response.status < 200 || response.status > 299) {
      return { ok: false, error: { code: "PAGE_CHANGED", message: "学校课表页面暂不可用，请稍后重试" } };
    }
    var entries = [];
    var latestWeek = 0;
    var rows = rowsOf(response.body, ["kbList", "items"]);
    for (var r = 0; r < rows.length; r++) {
      var row = rows[r];
      var name = field(row, ["kcmc", "KCMC"]);
      if (name === "") continue;
      var day = intField(row, ["xqj", "XQJ"]);
      // 优先 jcor(连上节次)，缺失时回退 jcs
      var periods = parsePeriods(field(row, ["jcor", "JCOR", "jcs", "JCS"]));
      if (day === null || day < 1 || day > 7 || periods === null) {
        return { ok: false, error: { code: "PAGE_CHANGED", message: "课表缺少可识别的星期或节次" } };
      }
      var weeks = parseWeeks(field(row, ["zcd", "ZCD", "weeks"]), MAX_WEEKS);
      var teacher = field(row, ["xm", "XM", "jsxm"]);
      var location = [field(row, ["xqmc", "cdxqmc"]), field(row, ["cdmc", "CDMC", "jxcdmc"])].filter(function (p) { return p !== ""; }).join(" ");
      var sourceId = field(row, ["schedule_source_id", "kcb_id", "kb_id", "jxb_id", "jx0404id", "kch_id"]);
      var seed = sourceId !== "" ? [sourceId, day, periods[0], periods[1], weeks.join(",")].join("|") : [name, teacher, location, day, periods[0], periods[1], weeks.join(",")].join("|");
      var entry = { id: entryId(seed), name: name, day: day, startPeriod: periods[0], endPeriod: periods[1], weeks: weeks };
      if (teacher !== "") entry.teacher = teacher;
      if (location !== "") entry.location = location;
      entries.push(entry);
      if (weeks.length > 0 && weeks[weeks.length - 1] > latestWeek) latestWeek = weeks[weeks.length - 1];
    }
    var maxWeeks = Math.min(MAX_WEEKS, Math.max(FALLBACK_WEEKS, latestWeek));
    return { ok: true, data: { termId: args.termId, entries: entries, maxWeeks: maxWeeks } };
  }
  // 北化默认节次时间（与学校作息一致，登录后自动写入设置，无需手动配置）
  var BUCT_PERIODS = [
    { number: 1, start: "08:00", end: "08:45" },
    { number: 2, start: "08:55", end: "09:40" },
    { number: 3, start: "10:00", end: "10:45" },
    { number: 4, start: "10:55", end: "11:40" },
    { number: 5, start: "14:00", end: "14:45" },
    { number: 6, start: "14:55", end: "15:40" },
    { number: 7, start: "16:00", end: "16:45" },
    { number: 8, start: "16:55", end: "17:40" },
    { number: 9, start: "19:00", end: "19:45" },
    { number: 10, start: "19:55", end: "20:40" },
    { number: 11, start: "20:50", end: "21:35" },
    { number: 12, start: "21:45", end: "22:30" }
  ];
  async function readCalendar(args, context, sdk) {
    var termId = (args && args.termId) || calendarTermId();
    var parts = termId.split("-");
    var year = Number(parts[0]);
    var semester = Number(parts[2]);
    return {
      ok: true,
      data: {
        termId: termId,
        startDate: termStartDate(year, semester),
        periods: BUCT_PERIODS
      }
    };
  }
  var index_default = {
    study: {
      terms: readTerms,
      schedule: readSchedule,
      calendar: readCalendar
    }
  };
  return index_default;
})();
globalThis.plugin = __schoolModule;
