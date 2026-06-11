/* ============================================================
   Markdown Reader (visionOS) — in-page engine.
   Ported from the JavaFX app's injected JS (anchor navigation,
   DOM-based find engine, scrollbar hiding, ratio-preserving zoom).
   The page is loaded once; content updates happen in place via
   mdApp.update() so the scroll position survives re-renders.
   ============================================================ */
(function () {
  'use strict';

  // ---------------------------------------------------------------- render

  // GitHub-style slug, mirroring the algorithm used by the JavaFX app:
  // lowercase, keep letters/digits/spaces/hyphens, spaces -> hyphens
  // (repeated hyphens are NOT collapsed).
  function slugify(s) {
    return (s || '').toLowerCase().trim()
      .replace(/[^\p{L}\p{N} -]/gu, '')
      .replace(/ /g, '-');
  }

  // marked v12 no longer generates heading ids; assign GitHub-style slugs so
  // in-document anchor links ("#8-voice-gemini") resolve. Duplicate slugs get
  // the usual -1, -2 suffixes.
  function assignHeadingIds(rootEl) {
    var seen = {};
    var hs = rootEl.querySelectorAll('h1,h2,h3,h4,h5,h6');
    for (var i = 0; i < hs.length; i++) {
      var slug = slugify(hs[i].textContent);
      if (!slug) { continue; }
      if (seen[slug] !== undefined) {
        seen[slug] += 1;
        slug = slug + '-' + seen[slug];
      } else {
        seen[slug] = 0;
      }
      hs[i].id = slug;
    }
  }

  // Only highlight fenced blocks that explicitly declare a language
  // (marked emits `<code class="language-xxx">`). Blocks with no language
  // are marked as a neutral "note" so they are never mis-colored.
  function highlightCode(rootEl) {
    rootEl.querySelectorAll('pre code').forEach(function (block) {
      var match = /\blanguage-([\w-]+)\b/.exec(block.className || '');
      if (window.hljs && match) {
        try {
          hljs.highlightElement(block);
        } catch (e) {
          block.classList.add('plaintext-note');
        }
      } else {
        block.classList.add('plaintext-note');
      }
    });
  }

  function contentEl() { return document.getElementById('content'); }

  // Replaces the rendered content in place. window.scrollY is naturally
  // preserved because the document is not reloaded (clamped by the browser
  // if the new content is shorter).
  function update(markdown) {
    var el = contentEl();
    el.innerHTML = marked.parse(markdown);
    assignHeadingIds(el);
    highlightCode(el);
    if (_findQuery) { findAll(_findQuery); }
  }

  // ---------------------------------------------------------------- scroll

  function maxScroll() {
    return Math.max(0, document.documentElement.scrollHeight - window.innerHeight);
  }

  function scrollRatio() {
    var max = maxScroll();
    return max > 0 ? Math.min(1, Math.max(0, window.scrollY / max)) : 0;
  }

  function scrollToRatio(r) {
    window.scrollTo(0, Math.min(1, Math.max(0, r)) * maxScroll());
  }

  // Zoom: changing the root font size changes the document height, so the
  // scroll position is preserved as a fraction of the scrollable range —
  // the reader stays anchored on the same passage instead of jumping to top.
  function setFontScale(percent) {
    var ratio = scrollRatio();
    document.getElementById('md-font').textContent =
      ':root { font-size: ' + percent + '%; }';
    scrollToRatio(ratio);
  }

  function setTheme(dark) {
    document.documentElement.className = dark ? 'theme-dark' : 'theme-light';
    var hlDark = document.getElementById('hl-dark');
    var hlLight = document.getElementById('hl-light');
    if (hlDark) { hlDark.media = dark ? 'all' : 'not all'; }
    if (hlLight) { hlLight.media = dark ? 'not all' : 'all'; }
  }

  // Hides only the scrollbar track/thumb; overflow stays untouched, so
  // gaze/pinch scrolling and keyboard navigation keep working while the
  // bar is invisible.
  function setScrollbarHidden(hidden) {
    var s = document.getElementById('md-hide-sb');
    if (hidden && !s) {
      s = document.createElement('style');
      s.id = 'md-hide-sb';
      s.textContent = '::-webkit-scrollbar{width:0;height:0;display:none}'
        + 'html{scrollbar-width:none}';
      document.head.appendChild(s);
    } else if (!hidden && s) {
      s.parentNode.removeChild(s);
    }
  }

  // ---------------------------------------------------------------- anchors

  // In-document anchor links (<a href="#fragment">) scroll the preview to
  // the matching heading. A delegated listener survives content re-renders.
  // Resolution is resilient: getElementById first, then a slug comparison
  // against every heading's text.
  function findTarget(frag) {
    if (!frag) { return null; }
    var el = document.getElementById(frag);
    if (el) { return el; }
    var named = document.getElementsByName(frag);
    if (named && named.length) { return named[0]; }
    var hs = document.querySelectorAll('h1,h2,h3,h4,h5,h6');
    for (var i = 0; i < hs.length; i++) {
      if (slugify(hs[i].textContent) === frag) { return hs[i]; }
    }
    return null;
  }

  document.addEventListener('click', function (e) {
    var a = e.target && e.target.closest ? e.target.closest('a[href^="#"]') : null;
    if (!a) { return; }
    var href = a.getAttribute('href') || '';
    var frag;
    try { frag = decodeURIComponent(href.slice(1)); }
    catch (err) { frag = href.slice(1); }
    var t = findTarget(frag);
    if (t) {
      e.preventDefault();
      e.stopPropagation();
      t.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  });

  // External links: notify the native side instead of navigating away.
  document.addEventListener('click', function (e) {
    var a = e.target && e.target.closest ? e.target.closest('a[href]') : null;
    if (!a) { return; }
    var href = a.getAttribute('href') || '';
    if (href.charAt(0) === '#') { return; } // handled above
    e.preventDefault();
    if (window.webkit && window.webkit.messageHandlers.mdOpenLink) {
      window.webkit.messageHandlers.mdOpenLink.postMessage(href);
    }
  });

  // ---------------------------------------------------------------- find

  // DOM-based find engine, ported as-is from the JavaFX app. Wraps every
  // case-insensitive match in <mark class="md-find-hit">; the active match
  // also carries .md-find-current and is centred in the viewport. Text
  // inside <script>, <style> and existing <mark> elements is skipped.
  var _hits = [];
  var _cur = -1;
  var _findQuery = '';

  function clearHits() {
    for (var i = 0; i < _hits.length; i++) {
      var m = _hits[i];
      if (!m.parentNode) { continue; }
      var p = m.parentNode;
      while (m.firstChild) { p.insertBefore(m.firstChild, m); }
      p.removeChild(m);
    }
    if (document.body) { document.body.normalize(); }
    _hits = [];
    _cur = -1;
  }

  function getTextNodes() {
    if (!document.body) { return []; }
    var walker = document.createTreeWalker(document.body, 4, null, false);
    var nodes = [];
    var n;
    while ((n = walker.nextNode())) { nodes.push(n); }
    return nodes.filter(function (node) {
      var p = node.parentNode;
      while (p && p.nodeType === 1) {
        var tn = p.tagName.toLowerCase();
        if (tn === 'script' || tn === 'style' || tn === 'mark') { return false; }
        p = p.parentNode;
      }
      return true;
    });
  }

  function scrollToHit(el) {
    var rect = el.getBoundingClientRect();
    var top = (window.pageYOffset || document.documentElement.scrollTop || 0);
    var target = rect.top + top - (window.innerHeight / 2);
    if (target < 0) { target = 0; }
    window.scrollTo(0, target);
  }

  function findAll(query) {
    clearHits();
    _findQuery = query || '';
    if (!query) { return 0; }
    var q = query.toLowerCase();
    var qLen = q.length;
    var nodes = getTextNodes();
    for (var i = 0; i < nodes.length; i++) {
      var node = nodes[i];
      var text = node.nodeValue;
      var lower = text.toLowerCase();
      var start = 0;
      var idx;
      var frags = [];
      while ((idx = lower.indexOf(q, start)) >= 0) {
        if (idx > start) { frags.push(document.createTextNode(text.slice(start, idx))); }
        var mark = document.createElement('mark');
        mark.className = 'md-find-hit';
        mark.textContent = text.slice(idx, idx + qLen);
        frags.push(mark);
        _hits.push(mark);
        start = idx + qLen;
      }
      if (frags.length > 0) {
        if (start < text.length) { frags.push(document.createTextNode(text.slice(start))); }
        var parent = node.parentNode;
        for (var j = 0; j < frags.length; j++) { parent.insertBefore(frags[j], node); }
        parent.removeChild(node);
      }
    }
    if (_hits.length > 0) {
      _cur = 0;
      _hits[0].classList.add('md-find-current');
      scrollToHit(_hits[0]);
    }
    return _hits.length;
  }

  function moveTo(idx) {
    if (_hits.length === 0) { return; }
    if (_cur >= 0 && _cur < _hits.length) { _hits[_cur].classList.remove('md-find-current'); }
    _cur = ((idx % _hits.length) + _hits.length) % _hits.length;
    _hits[_cur].classList.add('md-find-current');
    scrollToHit(_hits[_cur]);
  }

  function findNext() {
    if (_hits.length === 0) { return 0; }
    moveTo(_cur + 1);
    return _cur + 1;
  }

  function findPrev() {
    if (_hits.length === 0) { return 0; }
    moveTo(_cur - 1);
    return _cur + 1;
  }

  function findClear() {
    _findQuery = '';
    clearHits();
  }

  // ---------------------------------------------------------------- export

  window.mdApp = {
    update: update,
    setFontScale: setFontScale,
    setTheme: setTheme,
    setScrollbarHidden: setScrollbarHidden,
    scrollToRatio: scrollToRatio,
    findAll: findAll,
    findNext: findNext,
    findPrev: findPrev,
    findClear: findClear
  };
})();
