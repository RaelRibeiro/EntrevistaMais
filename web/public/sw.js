const CACHE = 'entrevistamais-v1';

const PRECACHE = [
  '/login',
  '/icone-192.png',
  '/icone-512.png',
  '/apple-touch-icon.png',
  '/manifest.webmanifest',
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches
      .open(CACHE)
      .then((cache) => cache.addAll(PRECACHE))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((chaves) =>
        Promise.all(
          chaves.filter((chave) => chave !== CACHE).map((chave) => caches.delete(chave))
        )
      )
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const { request } = event;
  if (request.method !== 'GET') return;

  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;

  const estatica = url.pathname.startsWith('/_next/static/') || url.pathname.startsWith('/_next/image');

  if (estatica) {
    event.respondWith(
      caches.match(request).then(
        (cacheada) =>
          cacheada ||
          fetch(request).then((resposta) => {
            const copia = resposta.clone();
            caches.open(CACHE).then((cache) => cache.put(request, copia));
            return resposta;
          })
      )
    );
    return;
  }

  if (request.mode === 'navigate') {
    event.respondWith(
      fetch(request)
        .then((resposta) => {
          const copia = resposta.clone();
          caches.open(CACHE).then((cache) => cache.put(request, copia));
          return resposta;
        })
        .catch(() =>
          caches
            .match(request)
            .then((cacheada) => cacheada || caches.match('/login'))
        )
    );
    return;
  }

  event.respondWith(
    caches.match(request).then(
      (cacheada) => cacheada || fetch(request)
    )
  );
});
