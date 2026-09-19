# ورشة الريسي للألومنيوم — Al-Raisi Aluminum Workshop

تطبيق Android (Kotlin + Jetpack Compose + Room) لتصميم وتصنيع شبابيك وأبواب الألمنيوم:
مصمم مرئي CAD عربي RTL متصل بمحرك حسابات قاعدة-القواعد (Rule-Based).

## التشغيل
1. افتح مجلد المشروع في Android Studio (Hedgehog أو أحدث).
2. انتظر اكتمال Gradle Sync (يتطلب اتصال إنترنت أول مرة).
3. شغّل على جهاز/محاكي Android 7.0+ (API 24).

## بنية الحسابات (قابلة للتعديل دون إعادة بناء)
كل قاعدة قص معادلة نصية في جدول `profiles`:
externalF / internalF / glassF / dividerF / fixedF + تغطية البركلوز + البركلوز المتوافق.
المتغيرات: opening, w, width, depth, span, topCover, bottomCover, leftCover, rightCover.
الدوال: min max ceil floor round abs sqrt sin cos asin pow — انظر `core/rules/Formula.kt`.
قواعد الإكسسوارات في جدول `accessory_rules` (conditionF + qtyF).

## التدفق
أبعاد الفتحة (مستطيل/قوس: الجالس والكلي) ← اختيار الإطار ← إنشاء الإطار تلقائياً
(قوس دائري/قطع مكافئ بتقطيع مفصلي وزواياه) ← المصمم: سحب/إفلات، التقاط، تحجيم،
تدوير، تراجع/إعادة، زوم/بان، أرقام دقيقة، محاذاة ← بركلوز تلقائي (ليس على الدرفة)
← إكسسوارات تلقائية ← قص ← زجاج ← مواسير 6م (FFD + هالك) ← تكاليف وهامش ربح ← تقرير PDF.
