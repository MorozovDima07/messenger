document.addEventListener('DOMContentLoaded', () => {
  const chatListEl     = document.getElementById('chats-list');
  const searchInput    = document.getElementById('chatName');
  const cleanBtn       = document.getElementById('cleanSearch');
  const sentinel       = document.getElementById('sentinel');
  const noChatsMessage = document.getElementById('no-chats-message');

  let page = 0;
  const size        = parseInt(chatListEl.dataset.size, 10) || 10;
  const totalPages  = () => parseInt(chatListEl.dataset.totalPages, 10) || 0;
  const chatType    = () => document.getElementById('chatType').value || '';
  const csrfToken   = document.querySelector('input[name="_csrf"]').value;

  function debounce(fn, wait) {
    let t;
    return (...args) => {
      clearTimeout(t);
      t = setTimeout(() => fn.apply(this, args), wait);
    };
  }

  function resetList() {
    page = 0;
    chatListEl.innerHTML = '';
    // прячем сообщение на старте нового поиска:
    noChatsMessage.style.display = 'none';
    chatListEl.appendChild(sentinel);
  }

  function renderChats(chats) {
    // 1) Первичный запрос и ничего нет
    if (page === 0 && chats.length === 0) {
      noChatsMessage.style.display = 'block';
      return;
    }

    // 2) Первичный запрос и что-то есть
    if (page === 0 && chats.length > 0) {
      noChatsMessage.style.display = 'none';
    }

    // далее — обычная вёрстка для чатов
    const fragment = document.createDocumentFragment();
    chats.forEach(chat => fragment.appendChild(createChatItem(chat)));
    fragment.appendChild(sentinel);
    chatListEl.appendChild(fragment);
  }

  async function loadChats(reset = false) {
    if (reset) resetList();
    if (!reset && page >= totalPages()) return;

    const params = new URLSearchParams({ page, size });
    const name  = searchInput.value.trim();
    if (name) params.append('chatName', name);
    const type  = chatType();
    if (type) params.append('chatType', type);

    document.getElementById('loading').style.display = 'block';
    try {
      const resp = await fetch(`/chats/load?${params.toString()}`, {
        credentials: 'same-origin',
        headers: {
          'X-CSRF-TOKEN': csrfToken,
          'X-Requested-With': 'XMLHttpRequest'
        }
      });
      if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
      const pageData = await resp.json();

      // апдейтим мета для пагинации
      chatListEl.dataset.page       = pageData.number;
      chatListEl.dataset.totalPages = pageData.totalPages;

      renderChats(pageData.content);
      page++;
    } catch (err) {
      console.error('Ошибка загрузки чатов:', err);
    } finally {
      document.getElementById('loading').style.display = 'none';
    }
  }

  function createChatItem(chat) {
    const li = document.createElement('li');
    li.className = 'item-li';
    li.dataset.chatId    = chat.id;
    li.dataset.chatType  = chat.type;
    li.dataset.userEmail = chat.isPersonal ? (chat.recipientEmail || '') : '';

    const a = document.createElement('a');
    a.href = chat.isPersonal
      ? '/direct'
      : `/group?id=${chat.id}&page=${page}&size=${size}`;
    a.className = 'chats-item' + (chat.active ? ' active-item' : '');

    const circle = document.createElement('div');
    circle.className = 'circle';
    const img = document.createElement('img');
    img.alt = 'Avatar';
    img.src = chat.avatar
      ? chat.avatar
      : (chat.type === 'GROUP'
         ? '/static/icons/group.svg'
         : '/static/icons/user.svg');
    circle.appendChild(img);
    a.appendChild(circle);

    const infoUl = document.createElement('ul');
    infoUl.className = 'mess-info';
    infoUl.innerHTML = `
      <li class="guy-mess">
        <span>${chat.name}</span>
        ${chat.isPersonal && chat.recipientEmail
          ? `<span class="online-indicator-name" data-user-email="${chat.recipientEmail}"></span>`
          : ''
        }
      </li>
      <li class="last-mess">${chat.lastMessage || ''}</li>`;
    a.appendChild(infoUl);

    const dateDiv = document.createElement('div');
    dateDiv.className = 'date-last-mess';
    if (chat.unreadCount > 0) {
      const unread = document.createElement('div');
      unread.className = 'circleUnreadCount';
      unread.textContent = chat.unreadCount;
      dateDiv.appendChild(unread);
    }
    const pDate = document.createElement('p');
    pDate.textContent = chat.lastMessageDate || '';
    dateDiv.appendChild(pDate);

    a.appendChild(dateDiv);
    li.appendChild(a);
    return li;
  }

  const onSearch = debounce(() => loadChats(true), 300);
  searchInput.addEventListener('input', (e) => {
    // показываем/прячем кнопку очистки
    cleanBtn.style.display = e.target.value.trim() ? 'block' : 'none';
    onSearch();
  });
  cleanBtn.style.display = 'none';
  cleanBtn.addEventListener('click', e => {
    e.preventDefault();
    searchInput.value = '';
    cleanBtn.style.display = 'none';
    loadChats(true);
  });

  const io = new IntersectionObserver(entries => {
    if (entries[0].isIntersecting) loadChats();
  }, { threshold: 1.0 });
  io.observe(sentinel);

  // первая загрузка
  loadChats(true);
});
