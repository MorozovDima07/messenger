document.addEventListener('DOMContentLoaded', function () {
    const input = document.getElementById('chatName');
    const cleanSearch = document.getElementById('cleanSearch');
    const chatType = document.getElementById('chatType');
    const chatListItems = document.getElementById('chatListItems');
    const searchForm = document.querySelector('.search-form');

    function performSearch(value) {
        const trimmed = value.trim();
        cleanSearch.style.display = trimmed ? 'flex' : 'none';

        fetch('/messenger-1.0-SNAPSHOT/chats/search?chatName=' + encodeURIComponent(trimmed) +
            '&chatType=' + encodeURIComponent(chatType.value), {
            headers: { 'X-Requested-With': 'XMLHttpRequest' }
        })
        .then(res => res.text())
        .then(html => {
            chatListItems.innerHTML = html;
        });
    }

    input.addEventListener('input', function () {
        performSearch(input.value);
    });

    searchForm.addEventListener('submit', function (e) {
        e.preventDefault();
        performSearch(input.value);
    });

    cleanSearch.addEventListener('click', function () {
        input.value = '';
        performSearch('');
    });
});
