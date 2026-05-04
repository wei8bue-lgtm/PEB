// PEB Application JavaScript

// Get CSRF token
function getCsrfToken() {
    const token = document.querySelector('meta[name="_csrf"]');
    return token ? token.getAttribute('content') : null;
}

function getCsrfHeader() {
    const header = document.querySelector('meta[name="_csrf_header"]');
    return header ? header.getAttribute('content') : 'X-CSRF-TOKEN';
}

// Initialize application
document.addEventListener('DOMContentLoaded', function() {
    initializeSearch();
    initializeTooltips();
});

// Search functionality with wildcards
function initializeSearch() {
    const searchBox = document.getElementById('searchBox');
    if (searchBox) {
        searchBox.addEventListener('input', debounce(performSearch, 300));
    }
}

function performSearch(event) {
    const query = event.target.value;
    if (query.length < 2) return;

    fetch(`/api/search?query=${encodeURIComponent(query)}`)
        .then(response => response.json())
        .then(data => displaySearchResults(data))
        .catch(error => console.error('Search error:', error));
}

function displaySearchResults(results) {
    const resultsContainer = document.getElementById('searchResults');
    if (!resultsContainer) return;

    resultsContainer.innerHTML = '';
    results.forEach(result => {
        const resultElement = createResultElement(result);
        resultsContainer.appendChild(resultElement);
    });
}

function createResultElement(result) {
    const div = document.createElement('div');
    div.className = 'search-result';
    div.innerHTML = `
        <h4>${result.id} - ${result.name}</h4>
        <p>${result.department || ''}</p>
    `;
    div.addEventListener('click', () => navigateToObject(result.id));
    return div;
}

// Release revision workflow
function releaseRevision(objectId, revisionId) {
    if (!confirm('Are you sure you want to release this revision?')) {
        return;
    }

    const token = getCsrfToken();
    const header = getCsrfHeader();

    fetch(`/api/workflows/designs/${objectId}/revisions/${revisionId}/release`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            [header]: token
        }
    })
    .then(response => {
        if (response.ok) {
            alert('Release workflow started. You will receive an email notification.');
            location.reload();
        } else {
            alert('Failed to start release workflow');
        }
    })
    .catch(error => console.error('Release error:', error));
}

// Revise operation
function reviseObject(objectId) {
    const token = getCsrfToken();
    const header = getCsrfHeader();

    fetch(`/api/revisions/designs/${objectId}/revise`, {
        method: 'POST',
        headers: {
            [header]: token
        }
    })
    .then(response => response.json())
    .then(data => {
        alert('New revision created successfully');
        location.reload();
    })
    .catch(error => {
        alert('Failed to create revision');
        console.error('Revise error:', error);
    });
}

// Save As operation
function saveAsObject(objectId) {
    const newName = prompt('Enter name for the new object:');
    if (!newName) return;

    const token = getCsrfToken();
    const header = getCsrfHeader();

    fetch(`/api/revisions/designs/${objectId}/saveas?newName=${encodeURIComponent(newName)}`, {
        method: 'POST',
        headers: {
            [header]: token
        }
    })
    .then(response => response.json())
    .then(data => {
        alert(`Object copied successfully. New ID: ${data.id}`);
        navigateToObject(data.id);
    })
    .catch(error => {
        alert('Failed to copy object');
        console.error('Save As error:', error);
    });
}

// Utility functions
function debounce(func, wait) {
    let timeout;
    return function executedFunction(...args) {
        const later = () => {
            clearTimeout(timeout);
            func(...args);
        };
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
    };
}

function navigateToObject(objectId) {
    window.location.href = `/objects/${objectId}`;
}

function initializeTooltips() {
    // Initialize tooltips if using a library like Bootstrap
}