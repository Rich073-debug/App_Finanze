// Service worker: l'app funziona anche offline e si aggiorna da sola
const CACHE = 'finanze-v3';
const FILES = ['./', './index.html', './guida.html', './manifest.json', './icon-192.png', './icon-512.png'];
self.addEventListener('install', e => { e.waitUntil(caches.open(CACHE).then(c => c.addAll(FILES))); self.skipWaiting(); });
self.addEventListener('activate', e => { e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k !== CACHE).map(k => caches.delete(k))))); self.clients.claim(); });
// rete prima (così ricevi gli aggiornamenti), cache se sei offline.
// Le pagine si chiedono sempre "fresche" al server, senza usare la copia del browser.
self.addEventListener('fetch', e => {
  if (e.request.method !== 'GET') return;
  const pagina = e.request.mode === 'navigate' || /\.(html|js|json)$|\/$/.test(new URL(e.request.url).pathname);
  e.respondWith(
    fetch(e.request, pagina ? {cache: 'no-cache'} : {}).then(r => { const copy = r.clone(); caches.open(CACHE).then(c => c.put(e.request, copy)); return r; })
      .catch(() => caches.match(e.request, {ignoreSearch: true}).then(r => r || caches.match('./index.html')))
  );
});
