package com.innovation313.roshankhata.data

/**
 * What the product, bill, order, invoice and scheme records hold, asked for by the name of the field (15 Nov 2026, the catalog's
 * records group: "Urea ka barcode", "DAP ki registration number", "Urea ka batch kab expire hota hai", "supplier ke bill ka
 * number", "purchase order bheja ya nahi", "invoice mein kya kya likha", "Urea par koi scheme chal rahi hai").
 *
 * Only words: this file reads a sentence and says which field or paper it asks for. What the answer says, and who may hear it,
 * is `HudhudFacts` (the app) with its gate; nothing here reads the book. The rules are Hudhud's rules alone: the bank has no
 * meaning for any of these asks, so `Hudhud.decide` never lets the sense turn them (lesson 145).
 *
 * The lists are whole words or runs of whole words ([VoiceQuestion.saysAny]), in the nine languages. The Sindhi, Farsi and
 * Bengali words are for a native read.
 */
internal object HudhudRecords {

    // ------------------------------------------------------------------------------------------ a product's own fields

    /** The code printed on the pack. */
    private val BARCODE = listOf("barcode", "bar code", "barcodes", "barkod", "code", "qr code", "بارکوڈ", "بار کوڈ", "کوڈ", "باركود", "بار كود", "بارڪوڊ", "بار ڪوڊ", "ڪوڊ",
        "رمز المنتج", "کد", "بارکد", "बारकोड", "बार कोड", "कोड", "বারকোড", "বার কোড", "কোড", "kode", "kode batang", "barkode")

    /** What the label on the pack says about the product: its registration, active ingredient, formulation, category and type. */
    private val REGISTRATION = listOf("registration", "registration number", "registration no", "reg no", "reg number", "reg", "registeration",
        "rajistration", "rajistaration", "register number", "رجسٹریشن", "رجسٹریشن نمبر", "رجسٹر نمبر", "رجسٹریشن", "رجسٹریشن نمبر", "رجسٽريشن", "تسجيل",
        "رقم التسجيل", "ثبت", "شماره ثبت", "रजिस्ट्रेशन", "रजिस्ट्रेशन नंबर", "पंजीकरण", "पंजीकरण संख्या", "রেজিস্ট্রেশন", "নিবন্ধন", "নিবন্ধন নম্বর",
        "registrasi", "nomor registrasi", "nomor izin", "nomor izin edar")
    private val TECHNICAL = listOf("technical name", "technical", "active ingredient", "ingredient", "ingredients", "generic",
        "generic name", "ajza", "ajzaa", "tarkeeb", "tarkib", "formula", "ٹیکنیکل", "ٹیکنیکل نام", "اجزا", "اجزاء", "ترکیب", "فعال جزو",
        "المادة الفعالة", "المادة", "الاسم العلمي", "الاسم التقني", "مكونات", "نام فنی", "ماده موثره", "ترکیبات", "तकनीकी नाम", "तकनीकी",
        "सक्रिय तत्व", "घटक", "টেকনিক্যাল নাম", "টেকনিক্যাল", "সক্রিয় উপাদান", "উপাদান", "bahan aktif", "nama teknis", "kandungan",
        "komposisi")
    private val FORMULATION = listOf("formulation", "formulations", "فارمولیشن", "فارمولا", "شکل", "تركيبة",
        "صيغة", "شکل دارو", "فرمولاسیون", "फॉर्मुलेशन", "रूप", "ফর্মুলেশন", "formulasi", "bentuk")
    private val CATEGORY = listOf("category", "categories", "kategori", "catagory", "type", "qism", "kis qism", "kis type",
        "زمرہ", "کیٹیگری", "قسم", "نوع", "فئة", "تصنيف", "دسته", "دسته‌بندی", "نوع محصول", "श्रेणी", "कैटेगरी", "प्रकार", "किस्म", "ক্যাটাগরি", "ধরন",
        "ধরণ", "জাত", "jenis", "tipe")

    /** How a pack is made and sold. */
    private val PACK = listOf("pack", "packing", "pack size", "packet", "carton", "dibba", "dabba", "per pack", "pack mein", "pack me", "پیک", "پیکنگ",
        "پیک سائز", "پیکٹ", "ڈبہ", "ڈبے", "کارٹن", "عبوة", "علبة", "تعبئة", "بسته", "بسته‌بندی", "کارتن", "पैक", "पैकिंग", "पैकेट", "कार्टन",
        "डिब्बा", "প্যাক", "প্যাকিং", "প্যাকেট", "কার্টন", "kemasan", "isi")

    /** The tax on one unit sold. */
    private val TAX = listOf("tax", "gst", "sales tax", "vat", "ppn", "ٹیکس", "سیلز ٹیکس", "ضريبة", "ضريبة المبيعات", "مالیات", "ٽيڪس", "टैक्स",
        "जीएसटी", "ট্যাক্স", "pajak")

    /** The place on the shelf, the product's own note. */
    private val SHELF = listOf("shelf", "rack", "location", "jagah", "jaga", "shelf pe", "rack pe", "shelf par", "rack par", "note", "notes", "جگہ",
        "شیلف", "ریک", "رف", "نوٹ", "مکان", "رف پر", "شیلف پر", "ریک پر", "موقع", "موقعیت", "قفسه", "جایگاه", "یادداشت", "رف تي", "جاءِ", "जगह",
        "शेल्फ", "रैक", "नोट", "तक", "জায়গা", "তাক", "শেলফ", "র‍্যাক", "নোট", "rak", "tempat", "letak", "lokasi", "catatan")
    private val KEPT = listOf("rakhi", "rakha", "rakhe", "rakhi hui", "rakha hua", "rakhta", "kept", "stored", "placed", "رکھی", "رکھا",
        "رکھے", "رکھی ہے", "رکھا ہے", "رکھتے", "موضوعة", "موجودة", "مخزنة", "مخزن", "گذاشته", "قرار", "रखी", "रखा", "रखे", "রাখা", "রাখি", "রাখো",
        "disimpan", "ditaruh", "diletakkan", "disusun")

    /** When the product was put in the book. */
    private val ADDED = listOf("added", "add hua", "add kiya", "add kia", "add kia gaya", "created", "banaya", "banai", "bana", "shamil kiya",
        "shamil hua", "daala", "dala", "daali", "darj kiya", "darj hua", "entered", "registered", "اضافہ", "شامل کیا", "شامل ہوا", "ڈالا", "ڈالی",
        "درج کیا", "درج ہوا", "شامل کی گئی", "شامل ہوئی", "شامل کی", "ڈالی گئی", "درج کی گئی", "بنایا", "بنائی", "ایڈ کیا", "ایڈ ہوا", "أضيف", "أضفت", "أضفته", "تمت إضافة", "تمت الإضافة", "أنشئ", "أنشأت",
        "اضافه شد", "اضافه کردم", "اضافه کردی", "افزوده", "اضافه", "ساخته", "ثبت شد", "जोड़ा", "जोड़ी", "जोड़ा गया", "जुड़ा", "शामिल किया",
        "दर्ज किया", "बनाया", "বানিয়েছি", "যোগ করা হয়েছে", "যোগ করেছি", "যোগ হয়েছে", "যোগ", "তৈরি", "ditambahkan", "ditambah", "dibuat",
        "ditambahkan pada")

    /** A batch, its lot and its expiry. */
    private val BATCH = listOf("batch", "batches", "batch number", "batch no", "lot", "lot number", "expiry", "expiry date", "expire", "expires",
        "expiring", "expired", "expiration", "kab expire", "bach", "بیچ", "بیچ نمبر", "لاٹ", "ایکسپائری", "ایکسپائر", "میعاد", "تاریخ ختم", "تاریخ انقضا",
        "انقضاء", "انقضا", "ختم ہونے", "خراب ہونے", "ميعاد", "دفعة", "رقم الدفعة", "تاريخ الانتهاء", "انتهاء الصلاحية", "الصلاحية", "صلاحية", "تنتهي", "ينتهي", "انتهاء", "تاريخ انقضاء",
        "تاریخ انقضاء", "انقضای", "बैच", "बैच नंबर", "लॉट", "एक्सपायरी", "समाप्ति", "समाप्ति तिथि", "ব্যাচ", "ব্যাচ নম্বর", "লট", "মেয়াদ", "এক্সপায়ারি",
        "মেয়াদ শেষ", "kedaluwarsa", "tanggal kedaluwarsa", "masa berlaku", "nomor batch", "nomor lot")

    /** A scheme the company runs. */
    private val SCHEME = listOf("scheme", "schemes", "skeem", "iskeem", "offer", "rebate", "اسکیم", "سکیم", "اسکیمیں", "اسڪيم", "عروض",
        "طرح فروش", "स्कीम", "ऑफर", "স্কিম", "অফার", "skema", "promo", "diskon pembelian")

    /** Every word that names one of a product's own fields, for the follow-up that carries a product on ("aur uska barcode"). */
    val FIELD_WORDS: List<String> = BARCODE + REGISTRATION + TECHNICAL + FORMULATION + CATEGORY + PACK + TAX + SHELF + KEPT + ADDED + BATCH + SCHEME

    /**
     * Which of a product's own records the sentence asks for, or null. [figure] is the shop's own figure the sentence also reads as
     * ("Urea ka batch kab expire hota hai" reads as the expiring figure): only that one may stand beside a field.
     * The order decides a sentence that holds two (the batch before the label, the label before the shelf).
     */
    fun facet(joined: String, m: HudhudMind.Read, figure: VoiceQuestion.Kind?): Hudhud.GoodsFacet? {
        if (figure != null && figure != VoiceQuestion.Kind.EXPIRING) return null
        fun any(list: List<String>) = VoiceQuestion.saysAny(joined, list)
        return when {
            any(BATCH) || figure == VoiceQuestion.Kind.EXPIRING -> Hudhud.GoodsFacet.BATCHES
            any(BARCODE) -> Hudhud.GoodsFacet.BARCODE
            any(REGISTRATION) || any(TECHNICAL) || any(FORMULATION) || any(CATEGORY) -> Hudhud.GoodsFacet.LABEL
            any(SCHEME) -> Hudhud.GoodsFacet.SCHEME
            any(PACK) -> Hudhud.GoodsFacet.PACK
            any(TAX) -> Hudhud.GoodsFacet.TAX
            // "Kab add hua" needs a when and an adding word, so "kab aayi" (the last delivery) is never the day it was added.
            any(ADDED) && m.any(HudhudMind.C.WHEN, HudhudMind.C.DATE) -> Hudhud.GoodsFacet.ADDED
            any(SHELF) || (m.has(HudhudMind.C.WHERE) && any(KEPT)) -> Hudhud.GoodsFacet.SHELF
            else -> null
        }
    }

    // ---------------------------------------------------------------------------------------------- a code to look up

    /** A barcode typed or scanned: eight to fourteen figures in a row, in any script's digits, written in 0-9. */
    fun codeIn(heard: String): String? {
        val run = StringBuilder()
        var best: String? = null
        fun close() {
            if (run.length in 8..14) best = run.toString()
            run.clear()
        }
        for (ch in heard) {
            if (Character.isDigit(ch)) run.append(Character.digit(ch, 10)) else close()
        }
        close()
        return best
    }

    /** A sentence that asks which product a code belongs to: the barcode's word with the code (a bare long number could be a phone number). */
    fun asksWhoseCode(joined: String): Boolean = VoiceQuestion.saysAny(joined, BARCODE)

    // ------------------------------------------------------------------------------------------------------- the papers

    /** A bill a supplier sent, an order the shop sent a supplier, an invoice the shop made, the company schemes. */
    private val ORDER_STRONG = listOf("purchase order", "purchase orders", "po", "pos", "پرچیز آرڈر", "خریداری آرڈر", "خرید آرڈر", "پرچیز آرڈرز",
        "أمر شراء", "أمر الشراء", "أوامر الشراء", "طلب شراء", "طلب الشراء", "آرڊر خريداري", "خريداري آرڊر", "پرچيز آرڊر", "طلبية شراء", "سفارش خرید", "پرچیز", "پर्चेज ऑर्डर", "पर्चेज ऑर्डर", "খরিদ অর্ডার", "ক্রয় অর্ডার", "পারচেজ অর্ডার",
        "pesanan pembelian", "po pembelian")
    private val ORDER_WORD = listOf("order", "orders", "آرڈر", "آرڈرز", "ऑर्डर", "অর্ডার", "طلب", "طلبية", "سفارش", "pesanan", "pesan", "آرڊر", "آرڊرز")
    private val ORDER_SENT = listOf("bheja", "bheji", "bheje", "bhejna", "bhej", "sent", "send", "diya", "diye", "kiya", "kia", "kiye", "mangwaya",
        "mangwai", "mangwaye", "mangwana", "mangwane", "ordered", "placed", "received", "mila", "mili", "aaya", "aya", "pending", "baqi", "draft",
        "status", "bhejna", "بھیجا", "بھیجی", "بھیجے", "بھیجنا", "بھیج", "دیا", "دی", "کیا", "کی", "منگوایا", "منگوائی", "منگوانا", "ملا", "ملی", "آیا",
        "باقی", "ڈرافٹ", "موڪليو", "موڪلي", "ويو", "ڏنو", "أرسل", "أرسلت", "طلبت", "أرسلنا", "تم الإرسال", "مرسل", "وصل", "فرستادم", "فرستاده", "ارسال", "سفارش دادم", "داده", "رسید",
        "भेजा", "भेजी", "भेजे", "मंगवाया", "मंगवाई", "दिया", "किया", "मिला", "आया", "बाकी", "পাঠিয়েছি", "পাঠানো", "পাঠিয়েছ", "দিয়েছি", "দিয়েছ",
        "করেছি", "এসেছে", "dikirim", "mengirim", "memesan", "dipesan", "diterima", "belum")
    private val STATE_WORDS = listOf("status", "state", "halat", "haalat", "kya hua", "kahan tak", "pending", "bheja", "bheji", "sent", "received",
        "حالت", "اسٹیٹس", "بھیجا", "بھیجی", "حالة", "الحالة", "وضعیت", "स्थिति", "स्टेटस", "অবস্থা", "স্ট্যাটাস", "status")

    /** A word that makes "how many / how much" a question of a paper's number or contents. */
    private val COUNT_OVERRIDE = listOf("number", "nambar", "kitna maal", "kitna saman", "kitna samaan", "bill mein", "bill me", "in this bill",
        "in the bill", "inside the bill", "invoice mein", "نمبر", "کتنا مال", "کتنا سامان", "بل میں", "اس بل میں", "رقم الفاتورة", "رقم الفاتوره",
        "شماره", "نمبر بل", "नंबर", "कितना माल", "बिल में", "নম্বর", "কত মাল", "বিলে", "nomor", "dalam tagihan", "dalam faktur")

    /** The fields that name a paper's own contents: with one of them a bill's number or lines are asked even beside "pending". */
    private val PAPER_FIELD_STRICT = listOf("number", "nambar", "note", "notes", "kya kya", "kya likha", "items", "lines", "contents", "نمبر", "نوٹ",
        "کیا کیا", "آئٹمز", "رقم الفاتورة", "ملاحظة", "بنود", "شماره", "नंबर", "नोट", "क्या क्या", "নম্বর", "নোট", "nomor", "catatan")

    /** What a sentence asks of the lines or fields of a paper rather than of its money or its count. */
    private val PAPER_FIELD = listOf("number", "no", "nambar", "nmbr", "note", "notes", "kya kya", "kia kia", "kya likha", "kya likhe", "likha",
        "likhe", "likhi", "items", "item", "lines", "contents", "details", "kitna maal", "kitna saman", "maal", "saman", "rate", "rates", "kis rate",
        "kis bhao", "bhao", "qeemat", "price", "quantity", "qty", "miqdar", "unit", "batch", "expiry", "supplier ko", "kis supplier",
        "kis ko", "kisko", "نمبر", "نمبر کیا", "نوٹ", "کیا کیا", "کیا لکھا", "لکھا", "لکھے", "آئٹمز", "آئٹم", "تفصیل", "تفصیلات", "کتنا مال", "کتنا سامان",
        "مال", "سامان", "ریٹ", "کس ریٹ", "بھاؤ", "قیمت", "مقدار", "کس سپلائر", "کس کو", "رقم الفاتورة", "رقم", "ملاحظة", "ملاحظات", "ماذا", "ما هي",
        "بنود", "تفاصيل", "المواد", "الكمية", "السعر", "سعر", "كمية", "شماره", "یادداشت", "چی", "چه", "اقلام", "جزئیات", "مقدار", "قیمت", "नंबर", "नोट",
        "क्या क्या", "क्या लिखा", "आइटम", "विवरण", "कितना माल", "रेट", "भाव", "कीमत", "मात्रा", "কত", "নম্বর", "নোট", "কী কী", "কি কি", "কি লেখা",
        "আইটেম", "বিবরণ", "দাম", "রেট", "পরিমাণ", "nomor", "catatan", "isi", "apa saja", "barang", "tarif", "harga", "jumlah", "rincian", "detail")

    /**
     * Which paper a sentence asks about with no one named, or null. A paper is asked about only by what it holds or says
     * (its number, note, lines, rate, state), never by its word alone — "bill banao", "invoice bhejo", "quotation kitne hain" and
     * the bills still to pay are other readings (the documents page, the count, the bills' standing).
     */
    fun paper(joined: String, m: HudhudMind.Read, makes: Boolean, supplierSide: Boolean, purchaseSide: Boolean, billWord: Boolean,
              documents: Boolean, otherDocs: Boolean, utility: Boolean, counting: Boolean, named: Boolean = false, standing: Boolean = false): Hudhud.PaperOf? {
        fun any(list: List<String>) = VoiceQuestion.saysAny(joined, list)
        // "How many" beside the paper's own number or "what came in this bill" is a question of its lines, not a count of papers.
        val countsPapers = counting && !any(COUNT_OVERRIDE)
        if (makes || countsPapers || utility) return null
        // Somebody named: only a field word asks for the paper ("Ali ka bill batao" is his account).
        val field = any(PAPER_FIELD) || (!named && m.any(HudhudMind.C.WHAT, HudhudMind.C.WHICH, HudhudMind.C.TELL))
        // A purchase order: its own name, or "order" with a supplier or a verb of sending.
        if (!otherDocs && (any(ORDER_STRONG) ||
                (!documents && any(ORDER_WORD) && (supplierSide || m.has(HudhudMind.C.SUPPLIER) || any(ORDER_SENT) || any(STATE_WORDS))))
        ) return Hudhud.PaperOf.ORDER
        // An invoice the shop made: its lines and fields.
        // Arabic and Persian call the bill and the invoice by one word ("فاتورة", "فاکتور"): with the supplier's side said it is the bill.
        val fromSupplier = documents && !otherDocs && !named && (supplierSide || purchaseSide || m.has(HudhudMind.C.SUPPLIER))
        if (documents && !otherDocs && field && !fromSupplier) return Hudhud.PaperOf.INVOICE
        // A bill a supplier sent: with a word of the supplier's side and a field asked.
        // Which bills are still to pay, which are late: how the bills stand, not what one holds (the bills' own status answer).
        if (standing && !any(PAPER_FIELD_STRICT)) return null
        if ((billWord || fromSupplier) && (supplierSide || purchaseSide || m.any(HudhudMind.C.SUPPLIER, HudhudMind.C.CAME)) && field) return Hudhud.PaperOf.BILL
        return null
    }

    // ------------------------------------------------------------------------------------------- the schemes' standing

    /** What is asked of a scheme beyond the page that makes one: whether it runs, when it ends, how far, what it gives. */
    private val SCHEME_STATE = listOf("chal rahi", "chal rahe", "chal raha", "chalti", "active", "running", "current", "ongoing", "kab khatam", "kab tak",
        "khatam", "ends", "end", "ending", "deadline", "last date", "target", "kitna reh gaya", "kitni baqi", "kitna baqi", "progress", "kitna hua",
        "reward", "inam", "claim", "claimed", "mili", "mila", "earned", "expires", "expire", "which", "kaun si", "kon si", "konsi", "kaun kaun si", "aakhri tareekh", "poori hui", "pura hua", "چل رہی", "چل رہے", "چلتی", "جاری", "کب ختم", "کب تک", "ختم", "ہدف", "ٹارگٹ", "انعام", "کلیم", "کتنی باقی", "کتنا باقی",
        "کون سی", "کونسی", "تعمل", "جارية", "ساري", "متى تنتهي", "تنتهي", "الهدف", "المكافأة", "مكافأة", "أي", "جاری است", "فعال",
        "کی تمام", "تمام می", "هدف", "پاداش", "کدام", "کدوم", "चल रही", "चल रहे", "चालू", "कब खत्म", "कब तक", "खत्म", "लक्ष्य", "टारगेट", "इनाम",
        "कितनी बाकी", "कौन सी", "कौनसी", "চলছে", "চালু", "কবে শেষ", "কবে পর্যন্ত", "শেষ", "লক্ষ্য", "টার্গেট", "পুরস্কার", "কতটুকু বাকি",
        "কোন", "berlaku", "sedang berjalan", "aktif", "kapan berakhir", "berakhir", "target", "hadiah", "klaim", "yang mana")

    /** Whether a sentence that names the schemes asks how they stand rather than how to make one. */
    fun schemesStand(joined: String, m: HudhudMind.Read): Boolean = VoiceQuestion.saysAny(joined, SCHEME_STATE)
}
