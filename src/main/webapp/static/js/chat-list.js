document.addEventListener('DOMContentLoaded', () => {
    const chatsList = document.getElementById('chats-list');
    const loading = document.getElementById('loading');
    const sentinel = document.getElementById('sentinel');
    let isLoading = false;
    let hasReachedEnd = false;
    const meta = document.querySelector('meta[name="_csrf"]');
    const csrfToken = meta ? meta.getAttribute('content') : null;

    if (!chatsList || !loading || !sentinel) {
        console.log('Required elements not found: chatsList, loading, or sentinel');
        return;
    }

    let page = parseInt(chatsList.getAttribute('data-page')) || 0;
    const size = parseInt(chatsList.getAttribute('data-size')) || 10;
    const chatType = chatsList.getAttribute('data-chat-type') || '';
    const totalPages = parseInt(chatsList.getAttribute('data-total-pages')) || 0;
    const cache = new Map();

    console.log(`Initialized: page=${page}, size=${size}, chatType=${chatType}, totalPages=${totalPages}`);

    const observer = new IntersectionObserver((entries) => {
        console.log(`Sentinel visibility: isIntersecting=${entries[0].isIntersecting}, intersectionRatio=${entries[0].intersectionRatio}`);
        if (entries[0].isIntersecting && !isLoading && !hasReachedEnd) {
            console.log('Sentinel intersected: loading more chats');
            loadMoreChats();
        }
    }, { threshold: 0.1, rootMargin: '50px' });

    observer.observe(sentinel);

    async function loadMoreChats() {
        if (hasReachedEnd) {
            console.log('Already reached end of pages, stopping load');
            observer.unobserve(sentinel);
            return;
        }

        const cacheKey = `${page + 1}-${size}-${chatType}`;
        if (cache.has(cacheKey)) {
            appendChats(cache.get(cacheKey));
            page++;
            if (!hasMorePages(cache.get(cacheKey))) {
                hasReachedEnd = true;
                observer.unobserve(sentinel);
            }
            return;
        }

        isLoading = true;
        loading.style.display = 'block';

        try {
            if (page + 1 >= totalPages) {
                console.log(`Page ${page + 1} exceeds totalPages=${totalPages}, stopping load`);
                hasReachedEnd = true;
                observer.unobserve(sentinel);
                return;
            }

            const url = `/chats/load?page=${page + 1}&size=${size}` + (chatType ? `&chatType=${chatType}` : '');
            console.log(`Requesting chats: page=${page + 1}, size=${size}, chatType=${chatType}, url=${url}`);

            const response = await fetch(url, {
                headers: {
                    'Accept': 'application/json',
                    'X-CSRF-TOKEN': csrfToken
                }
            });

            console.log(`Received response for page ${page + 1}: status=${response.status}`);
            if (!response.ok) throw new Error('Ошибка загрузки чатов');

            const data = await response.json();
            console.log(`Loaded ${data.content.length} chats for page ${page + 1}, totalElements=${data.totalElements}, totalPages=${data.totalPages}`);

            cache.set(cacheKey, data);
            if (data.content.length > 0) {
                page++;
                appendChats(data);
                if (!hasMorePages(data)) {
                    hasReachedEnd = true;
                    observer.unobserve(sentinel);
                }
            } else {
                hasReachedEnd = true;
                observer.unobserve(sentinel);
            }
        } catch (error) {
            console.error(`Ошибка при загрузке чатов на странице ${page + 1}:`, error);
            loading.innerHTML = '<p class="error">Ошибка загрузки. Попробуйте снова.</p>';
            setTimeout(() => {
                loading.innerHTML = '<span class="spinner"></span> Загрузка...';
                loading.style.display = 'none';
                observer.observe(sentinel);
            }, 3000);
        } finally {
            isLoading = false;
            loading.style.display = 'none';
            console.log(`Loading finished: isLoading=${isLoading}, page=${page}, hasReachedEnd=${hasReachedEnd}`);
        }
    }

    function hasMorePages(data) {
        if (data.last !== undefined) {
            console.log(`hasMorePages: last=${data.last}, result=${!data.last}`);
            return !data.last;
        }
        if (data.totalPages !== undefined && data.number !== undefined) {
            const result = data.number + 1 < data.totalPages;
            console.log(`hasMorePages: number=${data.number}, totalPages=${data.totalPages}, result=${result}`);
            return result;
        }
        if (data.totalElements !== undefined && data.size !== undefined && data.number !== undefined) {
            const result = (data.number + 1) * data.size < data.totalElements;
            console.log(`hasMorePages: number=${data.number}, totalElements=${data.totalElements}, result=${result}`);
            return result;
        }
        return false;
    }

    function appendChats(data) {
        const sentinel = document.getElementById('sentinel');
        data.content.forEach(chat => {
            const li = document.createElement('li');
            li.className = 'item-li';

            const a = document.createElement('a');
            if (chat.type === 'GROUP') {
                a.href = `/group?id=${chat.id}`;
            } else if (chat.type === 'PERSONAL') {
                a.href = `/direct?id=${chat.id}`;
            }
            a.className = chat.active ? 'chats-item active-item' : 'chats-item';

            const circleDiv = document.createElement('div');
            circleDiv.className = 'circle';

            const img = document.createElement('img');
            img.loading = 'lazy';
            if (chat.type === 'GROUP') {
                img.src = '/static/icons/group.svg';
                img.alt = 'Group Chat';
            } else if (chat.type === 'PERSONAL') {
                img.src = chat.avatar ? chat.avatar : '/static/icons/user.svg';
                img.alt = chat.avatar ? 'Avatar' : 'Default Avatar';
            }
            circleDiv.appendChild(img);

            const messInfoUl = document.createElement('ul');
            messInfoUl.className = 'mess-info';

            const guyMessLi = document.createElement('li');
            guyMessLi.className = 'guy-mess';
            guyMessLi.textContent = chat.name;
            messInfoUl.appendChild(guyMessLi);

            const lastMessLi = document.createElement('li');
            lastMessLi.className = 'last-mess';
            lastMessLi.textContent = chat.lastMessage || '';
            messInfoUl.appendChild(lastMessLi);

            const dateLastMessDiv = document.createElement('div');
            dateLastMessDiv.className = 'date-last-mess';

            if (chat.unreadCount > 0) {
                const circleUnread = document.createElement('div');
                circleUnread.className = 'circleUnreadCount';
                circleUnread.textContent = chat.unreadCount;
                dateLastMessDiv.appendChild(circleUnread);
            }

            const dateP = document.createElement('p');
            dateP.textContent = chat.lastMessageDate || '';
            dateLastMessDiv.appendChild(dateP);

            a.appendChild(circleDiv);
            a.appendChild(messInfoUl);
            a.appendChild(dateLastMessDiv);
            li.appendChild(a);
            chatsList.insertBefore(li, sentinel);
        });
    }

    function debounce(func, wait) {
        let timeout;
        return function (...args) {
            clearTimeout(timeout);
            timeout = setTimeout(() => func.apply(this, args), wait);
        };
    }

    function continueLoadingIfNeeded() {
        if (isLoading || hasReachedEnd) {
            console.log(`Skipping load: isLoading=${isLoading}, hasReachedEnd=${hasReachedEnd}`);
            return;
        }

        const scrollTop = chatsList.scrollTop;
        const scrollHeight = chatsList.scrollHeight;
        const clientHeight = chatsList.clientHeight;
        const hasMore = hasMorePages({ number: page, totalPages: totalPages }) && page + 1 < totalPages;

        console.log(`Checking if more loading needed: scrollTop=${scrollTop}, scrollHeight=${scrollHeight}, clientHeight=${clientHeight}, hasMore=${hasMore}`);

        if (scrollTop > 0 && scrollHeight - (scrollTop + clientHeight) < 100 && hasMore) {
            console.log('Approaching end of scroll, continuing to load more chats');
            loadMoreChats();
        }
    }

    const debouncedContinueLoading = debounce(continueLoadingIfNeeded, 200);
    chatsList.addEventListener('scroll', debouncedContinueLoading);

    if (document.body.scrollHeight <= window.innerHeight + window.scrollY + 100 && page === 0) {
        console.log('Initial load triggered due to short list');
        loadMoreChats();
    }
});