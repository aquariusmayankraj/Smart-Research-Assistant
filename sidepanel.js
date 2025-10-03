document.addEventListener('DOMContentLoaded', () => {
    chrome.storage.local.get(['researchNotes'], function(result) {
        document.getElementById('notes').value = result.researchNotes || '';
    });

    document.getElementById('summerizeBtn').addEventListener('click', summerizeText);
    document.getElementById('saveNotesBtn').addEventListener('click', saveNotes);
});

function showLoading(isLoading) {
    const btn = document.getElementById('summerizeBtn');
    if (!btn) return;
    if (isLoading) {
        btn.disabled = true;
        btn.dataset._label = btn.textContent;
        btn.textContent = 'Summarizing...';
    } else {
        btn.disabled = false;
        if (btn.dataset._label) btn.textContent = btn.dataset._label;
    }
}

async function summerizeText() {
    try {
        showLoading(true);
        const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
        const [{ result }] = await chrome.scripting.executeScript({
            target: { tabId: tab.id },
            function: () => window.getSelection().toString(),
        });
        if (!result) {
            showResult("Please select some text to summarize");
            return;
        }

        // Build request
        const payload = { content: result, operation: 'summarize' };
        const response = await fetch('http://localhost:8080/api/research/process', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json, text/plain, */*'
            },
            body: JSON.stringify(payload),
        });

        const contentType = response.headers.get('content-type') || '';
        const bodyText = await response.text();
        const maybeJson = contentType.includes('application/json');
        const body = maybeJson ? (safeJsonParse(bodyText) ?? bodyText) : bodyText;

        if (!response.ok) {
            const msg = typeof body === 'string' ? body : (body.message || JSON.stringify(body));
            throw new Error(`API Error ${response.status}: ${msg}`);
        }

        const output = typeof body === 'string' ? body : (body.result || JSON.stringify(body));
        showResult(output.replace(/\n/g, '<br>'));
    } catch (error) {
        showResult(error.message || String(error));
    } finally {
        showLoading(false);
    }
}

function safeJsonParse(text) {
    try { return JSON.parse(text); } catch { return null; }
}

async function saveNotes(){
    const notes = document.getElementById('notes').value;
    chrome.storage.local.set({researchNotes: notes}, function() {
        alert("Notes saved successfully");
    });
}

function showResult(content){
    document.getElementById('results').innerHTML = 
    `<div class="result-item">
       <div class="result-content">${content}</div>
    </div>`
}
