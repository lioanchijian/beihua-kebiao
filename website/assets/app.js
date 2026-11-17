(function () {
  var toggle = document.getElementById("menuToggle");
  var nav = document.getElementById("nav");
  if (toggle && nav) {
    toggle.addEventListener("click", function () {
      var open = nav.classList.toggle("open");
      toggle.setAttribute("aria-expanded", open ? "true" : "false");
    });
    nav.querySelectorAll("a").forEach(function (link) {
      link.addEventListener("click", function () {
        nav.classList.remove("open");
        toggle.setAttribute("aria-expanded", "false");
      });
    });
  }
  document.querySelectorAll(".faq-item").forEach(function (item) {
    item.addEventListener("toggle", function () {
      if (item.open) {
        document.querySelectorAll(".faq-item").forEach(function (other) {
          if (other !== item) other.open = false;
        });
      }
    });
  });
})();
  var downloadLinks = document.querySelectorAll('a[href*="beihua-kebiao-latest.apk"]');
  if (downloadLinks.length) {
    var browserKey = "bhk_browser_id";
    var browserId = localStorage.getItem(browserKey);
    if (!browserId) {
      browserId = (window.crypto && crypto.randomUUID) ? crypto.randomUUID() : "web_" + Math.random().toString(36).slice(2) + Date.now().toString(36);
      localStorage.setItem(browserKey, browserId);
    }
    downloadLinks.forEach(function (link) {
      link.addEventListener("click", function () {
        try {
          fetch("/api/stats/download", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ installId: browserId, source: "website", version: "1.0.9.10" }),
            keepalive: true
          }).catch(function () {});
        } catch (e) {}
      });
    });
  }
