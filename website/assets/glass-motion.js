(function () {
  var reduce = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  var glassSelectors = ".stats-grid,.bento-card,.rail-card,.download-card,.feature-card,.timeline-item";
  var glassItems = document.querySelectorAll(glassSelectors);
  glassItems.forEach(function (el) {
    el.setAttribute("data-glass", "");
    if (reduce) return;
    el.addEventListener("pointermove", function (event) {
      var rect = el.getBoundingClientRect();
      el.style.setProperty("--mx", (event.clientX - rect.left) + "px");
      el.style.setProperty("--my", (event.clientY - rect.top) + "px");
    });
    el.addEventListener("pointerleave", function () {
      el.style.removeProperty("--mx");
      el.style.removeProperty("--my");
    });
  });

  if (reduce || !("IntersectionObserver" in window)) {
    document.querySelectorAll(".reveal").forEach(function (el) { el.classList.add("is-visible"); });
    return;
  }
  var revealTargets = document.querySelectorAll(".section-heading,.feature-card,.bento-card,.rail-card,.timeline-item,.download-card,.experience-panel,.experience-copy");
  revealTargets.forEach(function (el) { el.classList.add("reveal"); });
  var observer = new IntersectionObserver(function (entries) {
    entries.forEach(function (entry) {
      if (entry.isIntersecting) {
        entry.target.classList.add("is-visible");
        observer.unobserve(entry.target);
      }
    });
  }, { threshold: 0.12, rootMargin: "0px 0px -30px" });
  revealTargets.forEach(function (el) { observer.observe(el); });
})();