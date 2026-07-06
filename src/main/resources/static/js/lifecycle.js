// src/main/resources/static/js/lifecycle.js

/**
 * Lifecycle-Handler für Manufacturing-Objekte.
 *
 * Verwendet Event Delegation statt inline onclick,
 * da Thymeleaf th:onclick mit Strings aus Sicherheitsgründen
 * blockiert (XSS-Schutz).
 *
 * Die Daten werden sicher via data-* Attribute übertragen.
 */
document.addEventListener('DOMContentLoaded', function () {

    // ── Event Delegation für alle SaveAs-Buttons ──────────────
    // Funktioniert auch für dynamisch geladene Fragmente,
    // da der Listener auf document registriert wird.
    document.addEventListener('click', function (event) {

        const btn = event.target.closest('.mfg-saveas-btn');
        if (!btn) return;

        // Daten sicher aus data-* Attributen lesen
        const objectType  = btn.dataset.objectType;
        const objectId    = btn.dataset.objectId;
        const redirectUrl = btn.dataset.redirectUrl;

        handleMfgSaveAs(objectType, objectId, redirectUrl);
    });
});

/**
 * SaveAs-Dialog und API-Aufruf.
 *
 * @param {string} objectType  – z.B. "manufacturing-bom"
 * @param {string} objectId    – ID des Objekts
 * @param {string} redirectUrl – Basis-URL, z.B. "/product-data"
 */
function handleMfgSaveAs(objectType, objectId, redirectUrl) {

    // ── Schritt 1: Namen abfragen ─────────────────────────────
    const newName = prompt('Name für die neue Kopie eingeben:');
    if (!newName || !newName.trim()) return;

    // ── Schritt 2: CSRF-Token aus Meta-Tags lesen ─────────────
    const csrfMeta   = document.querySelector(
                            'meta[name="_csrf"]');
    const headerMeta = document.querySelector(
                            'meta[name="_csrf_header"]');

    if (!csrfMeta || !headerMeta) {
        alert('❌ CSRF-Token nicht gefunden. '
              + 'Bitte Seite neu laden.');
        return;
    }

    const csrfToken  = csrfMeta.getAttribute('content');
    const csrfHeader = headerMeta.getAttribute('content');

    // ── Schritt 3: API-Aufruf ─────────────────────────────────
    const url = `/api/manufacturing/${objectType}/${objectId}`
              + `/saveas?newName=${encodeURIComponent(
                    newName.trim())}`;

    fetch(url, {
        method:  'POST',
        headers: {
            [csrfHeader]: csrfToken
        }
    })
    .then(function (response) {
        // Fehler-Response mit Body parsen
        return response.json().then(function (data) {
            if (!response.ok) {
                throw new Error(
                    data.error
                    || `HTTP ${response.status}`
                );
            }
            return data;
        });
    })
    .then(function (data) {
        alert(
            '✅ Kopie erfolgreich erstellt!\n\n'
            + `ID: ${data.id}\n`
            + `Name: ${data.name}\n`
            + `Revision: ${data.revision}`
        );

        // Zur Detail-Seite des neuen Objekts navigieren
        const base = (redirectUrl || `/${objectType}`)
                         .replace(/\/$/, ''); // trailing slash
        window.location.href = `${base}/${data.id}`;
    })
    .catch(function (err) {
        alert(`❌ Fehler beim Erstellen der Kopie:\n\n`
              + err.message);
    });
}