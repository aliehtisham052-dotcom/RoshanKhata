/*
 * Roshan Khata — a customer's own page, opened in the customer's browser
 * (22 Oct 2026). The link is …/#<page id>.<key>: the part after # never
 * reaches any server. The page's file holds only sealed bytes; they are
 * opened here with the key, and everything shown goes in as text (no HTML).
 *
 * Words in the app's nine languages; the page says which one the shop uses
 * ("l"). Money is shown as the app shows it: 0-9 digits, thousands with a
 * comma and the fraction with a point (Indonesian the other way round), the
 * currency's own decimals ("k").
 */
(function () {
  "use strict";

  var W = {
    "en": {
      dir: "ltr", brand: "Roshan Khata", title: "My account",
      loading: "Loading…", incomplete: "This link is not complete. Ask your shop to send it again.",
      browser: "Please open this link in Chrome.", gone: "This page is no longer shared.",
      net: "Could not load. Check your internet and try again.",
      wrong: "This link does not open this page. Ask your shop to send it again.",
      paused: "Your shop has paused this page for now.",
      owe: "You owe the shop", owed: "The shop owes you", settled: "All settled", updated: "Updated {d}",
      c_goods: "Goods on credit", c_lent: "Taken on credit", c_back: "Goods returned", c_paid: "Paid",
      s_back: "Goods returned to you", s_paid: "Paid to you", s_goods: "Goods you supplied", s_got: "Received from you",
      foot: "Anyone with this link can open this page. The shop writes what it shows; Roshan Khata does not check it."
    },
    "ur": {
      dir: "rtl", brand: "روشن کھاتہ", title: "میرا حساب",
      loading: "لوڈ ہو رہا ہے…", incomplete: "یہ لنک مکمل نہیں۔ دکان سے دوبارہ بھیجنے کو کہیں۔",
      browser: "یہ لنک کروم میں کھولیں۔", gone: "یہ صفحہ اب شیئر نہیں ہے۔",
      net: "لوڈ نہیں ہو سکا۔ انٹرنیٹ دیکھ کر دوبارہ کوشش کریں۔",
      wrong: "یہ لنک اس صفحے کو نہیں کھولتا۔ دکان سے دوبارہ بھیجنے کو کہیں۔",
      paused: "دکان نے یہ صفحہ فی الحال روک دیا ہے۔",
      owe: "آپ کے ذمے", owed: "دکان کے ذمے", settled: "حساب برابر", updated: "تازہ کاری {d}",
      c_goods: "ادھار سامان", c_lent: "ادھار لیا", c_back: "سامان واپس", c_paid: "ادا کیا",
      s_back: "آپ کو سامان واپس", s_paid: "آپ کو ادائیگی", s_goods: "آپ کا دیا سامان", s_got: "آپ سے وصول",
      foot: "جس کے پاس یہ لنک ہو وہ یہ صفحہ کھول سکتا ہے۔ اس میں جو لکھا ہے وہ دکان لکھتی ہے؛ روشن کھاتہ اسے نہیں جانچتا۔"
    },
    "ur-Latn": {
      dir: "ltr", brand: "Roshan Khata", title: "Mera hisaab",
      loading: "Load ho raha hai…", incomplete: "Yeh link mukammal nahi. Dukaan se dobara bhejne ko kahein.",
      browser: "Yeh link Chrome mein kholein.", gone: "Yeh safha ab share nahi hai.",
      net: "Load nahi ho saka. Internet dekh kar dobara koshish karein.",
      wrong: "Yeh link is safhe ko nahi kholta. Dukaan se dobara bhejne ko kahein.",
      paused: "Dukaan ne yeh safha filhal rok diya hai.",
      owe: "Aap ke zimme", owed: "Dukaan ke zimme", settled: "Hisaab barabar", updated: "Taaza kaari {d}",
      c_goods: "Udhaar samaan", c_lent: "Udhaar liya", c_back: "Samaan wapas", c_paid: "Ada kiya",
      s_back: "Aap ko samaan wapas", s_paid: "Aap ko adaigi", s_goods: "Aap ka diya samaan", s_got: "Aap se wusool",
      foot: "Jis ke paas yeh link ho woh yeh safha khol sakta hai. Is mein jo likha hai woh dukaan likhti hai; Roshan Khata ise nahi jaanchta."
    },
    "sd": {
      dir: "rtl", brand: "روشن کاتو", title: "منهنجو حساب",
      loading: "لوڊ ٿي رهيو آهي…", incomplete: "هي لنڪ مڪمل ناهي. دڪان کي ٻيهر موڪلڻ لاءِ چئو.",
      browser: "هي لنڪ ڪروم ۾ کوليو.", gone: "هي صفحو هاڻي شيئر ناهي.",
      net: "لوڊ نه ٿي سگهيو. انٽرنيٽ ڏسي ٻيهر ڪوشش ڪريو.",
      wrong: "هي لنڪ هن صفحي کي نٿي کولي. دڪان کي ٻيهر موڪلڻ لاءِ چئو.",
      paused: "دڪان هي صفحو في الحال روڪي ڇڏيو آهي.",
      owe: "توهان جي ذمي", owed: "دڪان جي ذمي", settled: "حساب برابر", updated: "تازو ڪيو ويو {d}",
      c_goods: "اڌار سامان", c_lent: "اڌار ورتو", c_back: "سامان واپس", c_paid: "ادا ڪيو",
      s_back: "توهان کي سامان واپس", s_paid: "توهان کي ادائيگي", s_goods: "توهان جو ڏنل سامان", s_got: "توهان کان وصول",
      foot: "جنهن وٽ هي لنڪ هجي اهو هي صفحو کولي سگهي ٿو. ان ۾ جيڪو لکيل آهي اهو دڪان لکي ٿو؛ روشن کاتو ان کي نٿو جانچي."
    },
    "ar": {
      dir: "rtl", brand: "روشن كهاتا", title: "حسابي",
      loading: "جارٍ التحميل…", incomplete: "هذا الرابط غير مكتمل. اطلب من متجرك إرساله مرة أخرى.",
      browser: "يُرجى فتح هذا الرابط في Chrome.", gone: "لم تعد هذه الصفحة مشتركة.",
      net: "تعذّر التحميل. تحقّق من الإنترنت وحاول مرة أخرى.",
      wrong: "هذا الرابط لا يفتح هذه الصفحة. اطلب من متجرك إرساله مرة أخرى.",
      paused: "أوقف المتجر هذه الصفحة مؤقتًا.",
      owe: "أنت مدين للمتجر", owed: "المتجر مدين لك", settled: "الحساب مسدَّد", updated: "آخر تحديث {d}",
      c_goods: "بضاعة بالآجل", c_lent: "أُخذ بالآجل", c_back: "بضاعة مُرتجعة", c_paid: "مدفوع",
      s_back: "بضاعة أُعيدت إليك", s_paid: "دُفع لك", s_goods: "بضاعة وردتها", s_got: "استُلم منك",
      foot: "يستطيع أي شخص لديه هذا الرابط فتح هذه الصفحة. المتجر هو من يكتب ما فيها، ولا يتحقق منه روشن كهاتا."
    },
    "fa": {
      dir: "rtl", brand: "روشن خاتا", title: "حساب من",
      loading: "در حال بارگذاری…", incomplete: "این پیوند کامل نیست. از فروشگاهتان بخواهید دوباره بفرستد.",
      browser: "لطفاً این پیوند را در Chrome باز کنید.", gone: "این صفحه دیگر به اشتراک گذاشته نمی‌شود.",
      net: "بارگذاری نشد. اینترنت را بررسی کنید و دوباره امتحان کنید.",
      wrong: "این پیوند این صفحه را باز نمی‌کند. از فروشگاهتان بخواهید دوباره بفرستد.",
      paused: "فروشگاه این صفحه را فعلاً متوقف کرده است.",
      owe: "شما به فروشگاه بدهکارید", owed: "فروشگاه به شما بدهکار است", settled: "حساب تسویه است", updated: "به‌روزرسانی {d}",
      c_goods: "کالای نسیه", c_lent: "نسیه گرفته‌شده", c_back: "کالای برگشتی", c_paid: "پرداخت‌شده",
      s_back: "کالای برگشتی به شما", s_paid: "پرداخت به شما", s_goods: "کالایی که تحویل دادید", s_got: "دریافت از شما",
      foot: "هر کسی این پیوند را داشته باشد می‌تواند این صفحه را باز کند. آنچه در آن است را فروشگاه می‌نویسد؛ روشن خاتا آن را بررسی نمی‌کند."
    },
    "hi": {
      dir: "ltr", brand: "रोशन खाता", title: "मेरा हिसाब",
      loading: "लोड हो रहा है…", incomplete: "यह लिंक पूरा नहीं है। दुकान से इसे फिर भेजने को कहें।",
      browser: "कृपया यह लिंक Chrome में खोलें।", gone: "यह पेज अब शेयर नहीं है।",
      net: "लोड नहीं हो सका। इंटरनेट देखकर फिर कोशिश करें।",
      wrong: "यह लिंक इस पेज को नहीं खोलता। दुकान से इसे फिर भेजने को कहें।",
      paused: "दुकान ने यह पेज अभी के लिए रोक दिया है।",
      owe: "आपको दुकान को देना है", owed: "दुकान को आपको देना है", settled: "हिसाब बराबर", updated: "अपडेट {d}",
      c_goods: "उधार सामान", c_lent: "उधार लिया", c_back: "सामान वापस", c_paid: "चुकाया",
      s_back: "आपको सामान वापस", s_paid: "आपको भुगतान", s_goods: "आपका दिया सामान", s_got: "आपसे मिला",
      foot: "जिसके पास यह लिंक है वह यह पेज खोल सकता है। इसमें जो लिखा है वह दुकान लिखती है; रोशन खाता इसकी जाँच नहीं करता।"
    },
    "bn": {
      dir: "ltr", brand: "রোশন খাতা", title: "আমার হিসাব",
      loading: "লোড হচ্ছে…", incomplete: "এই লিংকটি সম্পূর্ণ নয়। দোকানকে আবার পাঠাতে বলুন।",
      browser: "দয়া করে লিংকটি Chrome-এ খুলুন।", gone: "এই পাতাটি আর শেয়ার করা নেই।",
      net: "লোড করা যায়নি। ইন্টারনেট দেখে আবার চেষ্টা করুন।",
      wrong: "এই লিংক এই পাতাটি খোলে না। দোকানকে আবার পাঠাতে বলুন।",
      paused: "দোকান আপাতত এই পাতাটি বন্ধ রেখেছে।",
      owe: "আপনার কাছে দোকানের পাওনা", owed: "দোকানের কাছে আপনার পাওনা", settled: "হিসাব মিটে গেছে", updated: "হালনাগাদ {d}",
      c_goods: "বাকিতে পণ্য", c_lent: "বাকিতে নেওয়া", c_back: "পণ্য ফেরত", c_paid: "পরিশোধ",
      s_back: "আপনাকে পণ্য ফেরত", s_paid: "আপনাকে পরিশোধ", s_goods: "আপনার দেওয়া পণ্য", s_got: "আপনার কাছ থেকে পাওয়া",
      foot: "এই লিংক যার কাছে আছে সে-ই এই পাতা খুলতে পারে। এতে যা লেখা থাকে তা দোকান লেখে; রোশন খাতা তা যাচাই করে না।"
    },
    "id": {
      dir: "ltr", brand: "Roshan Khata", title: "Rekening saya",
      loading: "Memuat…", incomplete: "Tautan ini tidak lengkap. Minta toko Anda mengirimnya lagi.",
      browser: "Silakan buka tautan ini di Chrome.", gone: "Halaman ini tidak lagi dibagikan.",
      net: "Gagal memuat. Periksa internet Anda lalu coba lagi.",
      wrong: "Tautan ini tidak membuka halaman ini. Minta toko Anda mengirimnya lagi.",
      paused: "Toko menghentikan halaman ini untuk sementara.",
      owe: "Anda berutang kepada toko", owed: "Toko berutang kepada Anda", settled: "Lunas", updated: "Diperbarui {d}",
      c_goods: "Barang dengan utang", c_lent: "Diambil dengan utang", c_back: "Barang dikembalikan", c_paid: "Dibayar",
      s_back: "Barang dikembalikan kepada Anda", s_paid: "Dibayar kepada Anda", s_goods: "Barang yang Anda kirim", s_got: "Diterima dari Anda",
      foot: "Siapa pun yang memiliki tautan ini dapat membuka halaman ini. Isinya ditulis oleh toko; Roshan Khata tidak memeriksanya."
    }
  };
  // Dates in each language's own month names where the browser has them, always with 0-9 digits.
  var DATE_LOCALE = { "en": "en-GB", "ur": "ur-PK", "ur-Latn": "en-GB", "sd": "sd", "ar": "ar", "fa": "fa", "hi": "hi-IN", "bn": "bn", "id": "id-ID" };

  var page = document.getElementById("page");
  var foot = document.getElementById("foot");

  function el(tag, cls, text) {
    var e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text != null) e.textContent = text;
    return e;
  }
  /** The reader's own language before the page says the shop's (the browser's, else English). */
  function guess() {
    var langs = (navigator.languages && navigator.languages.length ? navigator.languages : [navigator.language || "en"]);
    for (var i = 0; i < langs.length; i++) {
      var l = String(langs[i] || "").toLowerCase();
      if (l === "in" || l.indexOf("id") === 0) return "id";
      var two = l.slice(0, 2);
      if (W[two]) return two;
    }
    return "en";
  }
  function words(l) { return W[l] || W.en; }
  function setLang(l) {
    var w = words(l);
    document.documentElement.lang = l;
    document.documentElement.dir = w.dir;
    document.body.className = (l === "ur" ? "nastaliq" : "");
    document.title = w.brand + " · " + w.title;
    foot.textContent = "";
    foot.appendChild(el("span", null, w.foot));
    foot.appendChild(el("br"));
    foot.appendChild(el("span", "brand", w.brand + " · Innovation-313"));
  }
  /** A message before the page could be opened: in the reader's language, and in English beside it. */
  function say(key) {
    var l = guess();
    setLang(l);
    page.textContent = "";
    page.appendChild(el("p", "msg", words(l)[key]));
    if (l !== "en") page.appendChild(el("p", "msg en", W.en[key]));
  }
  function unb64url(s) {
    s = s.replace(/-/g, "+").replace(/_/g, "/");
    while (s.length % 4) s += "=";
    var bin = atob(s), out = new Uint8Array(bin.length);
    for (var i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
    return out;
  }
  /** As the app's Format.money: whole amounts without decimals, fractions to the currency's own. */
  function money(c, k, l, n) {
    var v = Math.abs(Number(n) || 0);
    var fixed = (v % 1 === 0 || k === 0) ? v.toFixed(0) : v.toFixed(k);
    var parts = fixed.split(".");
    parts[0] = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, ",");
    var s = parts.join(".");
    if (l === "id") s = s.replace(/[,.]/g, function (ch) { return ch === "," ? "." : ","; });
    // Isolated left to right, so an Urdu or Arabic page keeps "Rs 5,400" in one piece on its own side.
    return "\u2066" + (c ? c + " " : "") + s + "\u2069";
  }
  function day(ms, l) {
    if (!ms) return "";
    var opts = { day: "numeric", month: "short", year: "numeric", numberingSystem: "latn" };
    try { return new Date(ms).toLocaleDateString(DATE_LOCALE[l] || "en-GB", opts); }
    catch (e) { return new Date(ms).toLocaleDateString("en-GB", opts); }
  }
  /** Which way an entry went, said to the reader: a customer, or a supplier of the shop. */
  function kind(w, supplier, gave, goods) {
    if (supplier) return gave ? (goods ? w.s_back : w.s_paid) : (goods ? w.s_goods : w.s_got);
    return gave ? (goods ? w.c_goods : w.c_lent) : (goods ? w.c_back : w.c_paid);
  }

  function show(d) {
    var l = W[d.l] ? d.l : "en", w = words(l);
    setLang(l);
    page.textContent = "";
    if (d.z) { page.appendChild(el("p", "msg", w.paused)); return; }
    var k = (d.k === 0 || d.k === 1 || d.k === 2 || d.k === 3) ? d.k : 2;
    var b = Number(d.b) || 0;
    page.appendChild(el("p", "shop", d.s || ""));
    page.appendChild(el("h1", null, d.n || ""));
    var owes = b > 0.0005, owed = b < -0.0005;
    page.appendChild(el("p", "label", owes ? w.owe : owed ? w.owed : w.settled));
    page.appendChild(el("p", "amount " + (owes ? "owe" : "clear"), money(d.c, k, l, b)));
    if (d.t) page.appendChild(el("p", "label", w.updated.replace("{d}", day(d.t, l))));
    if (d.e && d.e.length) {
      var ul = el("ul");
      d.e.forEach(function (x) {
        var li = el("li"), left = el("div");
        left.appendChild(el("div", "when", day(x.d, l)));
        if (x.x) left.appendChild(el("div", "what", x.x));
        left.appendChild(el("span", "tag", kind(w, d.p === "s", x.g === 1, x.m === 1)));
        li.appendChild(left);
        li.appendChild(el("div", "sum", money(d.c, k, l, x.a)));
        ul.appendChild(li);
      });
      page.appendChild(ul);
    }
  }

  var m = /^#([0-9a-f]{32})\.([A-Za-z0-9_-]{22})$/.exec(location.hash);
  setLang(guess());
  page.textContent = "";
  page.appendChild(el("p", "msg", words(guess()).loading));
  if (!m) { say("incomplete"); return; }
  if (!window.crypto || !crypto.subtle || !window.fetch) { say("browser"); return; }

  fetch("d/" + m[1] + ".txt", { cache: "no-cache", credentials: "omit" }).then(function (r) {
    if (r.status === 404) throw "gone";
    if (!r.ok) throw "net";
    return r.text();
  }, function () { throw "net"; }).then(function (text) {
    text = text.trim();
    if (text === "!gone") throw "gone";
    var sealed = unb64url(text);
    return crypto.subtle.importKey("raw", unb64url(m[2]), "AES-GCM", false, ["decrypt"]).then(function (key) {
      return crypto.subtle.decrypt(
        { name: "AES-GCM", iv: sealed.slice(0, 12), additionalData: new TextEncoder().encode("rk-clink-v1"), tagLength: 128 },
        key, sealed.slice(12));
    });
  }).then(function (plain) {
    show(JSON.parse(new TextDecoder().decode(plain)));
  }).catch(function (e) {
    if (e === "gone") say("gone");
    else if (e === "net") say("net");
    else say("wrong");
  });
})();
