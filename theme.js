
/* Rotating page background — picks one of the bundled wallpapers per browser session
   and injects an override so pages keep their normal look until JS runs. */
(function () {
    var KEY = 'nekomo_bg_wall';
    var WALLS = [
        '/Images/nekomo-wallpaper.jpg',
        '/Images/nekomo-wallpaper-2.jpg',
        '/Images/nekomo-wallpaper-3.jpg'
    ];
    var pick = sessionStorage.getItem(KEY);
    if (!pick || WALLS.indexOf(pick) < 0) {
        pick = WALLS[Math.floor(Math.random() * WALLS.length)];
        try { sessionStorage.setItem(KEY, pick); } catch (e) {}
    }
    if (pick === WALLS[0]) return; // default wall — no override needed
    var st = document.createElement('style');
    st.textContent = '.bg-fx::after { background-image: url("' + pick + '") !important; }';
    document.head.appendChild(st);
})();
