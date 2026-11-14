package com.graphicarray.voicetyper

import android.graphics.Paint

/**
 * Emoji lists for the emoji panel. Plain text, one string per category, emojis split by spaces.
 * Add or remove emojis here. [list] drops the ones this phone's font cannot draw (no empty boxes on old Android).
 */
object Emojis {
    class Cat(val name: String, val icon: String, private val raw: String) {
        val all: List<String> get() = raw.trim().split(' ').filter { it.isNotEmpty() }
    }

    val CATS: List<Cat> = listOf(
        Cat("Smileys", "😀", "😀 😁 😂 😃 😄 😅 😆 😇 😈 😉 😊 😋 😌 😍 😎 😏 😐 😑 😒 😓 😔 😕 😖 😗 😘 😙 😚 😛 😜 😝 😞 😟 😠 😡 😢 😣 😤 😥 😦 😧 😨 😩 😪 😫 😬 😭 😮 😯 😰 😱 😲 😳 😴 😵 😶 😷 🙁 🙂 🙃 🙄 🤐 🤑 🤒 🤓 🤔 🤕 🤗 🤠 🤡 🤢 🤣 🤤 🤥 🤧 🤨 🤩 🤪 🤫 🤬 🤭 🤮 🤯 🥰 🥳 🥴 🥵 🥶 🥸 🥹 🥺 🧐 🙈 🙉 🙊 😺 😻 😼 😽 😾 😿 🙀 ❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 💕 💖 💗 💘 💝 💞 💟 💋 💯 💢 💥 💫 💦 💨 💤 💩 👻 👽 👾 🤖 💀 👿 👹 👺"),
        Cat("People", "👋", "👋 🤚 🖐️ ✋ 🖖 👌 🤏 ✌️ 🤞 🤟 🤘 🤙 👆 👇 👈 👉 👊 👍 👎 👏 👐 🙌 🙏 🤝 ✍️ 💅 🤳 💪 🦵 🦶 👂 👃 👀 👁️ 👅 👄 🧠 👦 👧 👨 👩 👴 👵 👪 👰 👱 👲 👳 👷 👸 💂 🧑 🙅 🙆 🙇 💁 💃 🕺 🤦 🤷 🤸 🏃 🏊 🚴 🏄 👫 👬 👭"),
        Cat("Animals", "🐻", "🐀 🐁 🐂 🐃 🐄 🐅 🐆 🐇 🐈 🐉 🐊 🐋 🐌 🐍 🐎 🐏 🐐 🐑 🐒 🐓 🐔 🐕 🐖 🐗 🐘 🐙 🐚 🐛 🐜 🐝 🐞 🐟 🐠 🐡 🐢 🐣 🐤 🐥 🐦 🐧 🐨 🐩 🐪 🐫 🐬 🐭 🐮 🐯 🐰 🐱 🐲 🐳 🐴 🐵 🐶 🐷 🐸 🐹 🐺 🐻 🐼 🐽 🐾 🦀 🦁 🦂 🦃 🦄 🦅 🦆 🦇 🦈 🦉 🦊 🦋 🦌 🦍 🦎 🦏 🦐 🦑 🦒 🦓 🦔 🦕 🦖 🦗 🌱 🌲 🌳 🌴 🌵 🌷 🌸 🌹 🌺 🌻 🌼 🌾 🌿 🍀 🍁 🍂 🍃 🍄 💐 ☀️ 🌙 🌟 ⭐ 🌈 ☁️ ⚡ ❄️ ⛄ 🔥 💧 🌊 🌍 🌎 🌏"),
        Cat("Food", "🍔", "🍅 🍆 🍇 🍈 🍉 🍊 🍋 🍌 🍍 🍎 🍏 🍐 🍑 🍒 🍓 🍔 🍕 🍖 🍗 🍘 🍙 🍚 🍛 🍜 🍝 🍞 🍟 🍠 🍡 🍢 🍣 🍤 🍥 🍦 🍧 🍨 🍩 🍪 🍫 🍬 🍭 🍮 🍯 🍰 🍱 🍲 🍳 🍴 🍵 🍶 🍷 🍸 🍹 🍺 🍻 🍼 🌭 🌮 🌯 🥐 🥑 🥒 🥓 🥔 🥕 🥖 🥗 🥘 🥙 🥚 🥛 🥜 🥝 🥞 🥟 🥢 🥣 🥤 🥥 🥦 🥧 🥨 🥩 🥪 🥫 🧀 🧁"),
        Cat("Activity", "⚽", "⚽ ⚾ 🏀 🏈 🎾 🏐 🏑 🏒 🏓 🏸 🥊 🥋 🥌 🥍 🥎 🥏 🎯 🎱 🎳 🎮 🎲 🎰 🎤 🎥 🎦 🎧 🎨 🎬 🎭 🎹 🎷 🎸 🎺 🎻 🥁 🎵 🎶 🏆 🏅 🥇 🥈 🥉 🎃 🎄 🎆 🎇 🎈 🎉 🎊 🎀 🎁"),
        Cat("Travel", "🚗", "🚀 🚁 🚂 🚃 🚄 🚅 🚆 🚇 🚈 🚉 🚊 🚋 🚌 🚍 🚎 🚏 🚐 🚑 🚒 🚓 🚔 🚕 🚖 🚗 🚘 🚙 🚚 🚛 🚜 🚝 🚞 🚟 🚠 🚡 🚢 🚣 🚤 🚥 🚦 🚧 🚨 🚲 🛴 🛵 🛶 ✈️ ⛵ 🏠 🏡 🏢 🏣 🏤 🏥 🏦 🏧 🏨 🏩 🏪 🏫 🏬 🏭 🏮 🏯 🏰 🗻 🗼 🗽 🗾 🗿 ⛺ ⛲ 🎡 🎢 🌇 🌆 🌃 🌉 🌄 🌅"),
        Cat("Objects", "💡", "⌚ 📱 💻 💡 💽 💾 💰 💱 💲 💳 💴 💵 💶 💷 💸 📖 📚 📛 📜 📝 📦 📧 📌 📎 📏 📐 📷 📺 📻 🔋 🔌 🔍 🔐 🔑 🔒 🔓 🔔 🔧 🔨 🔩 🔮 🎩 👓 👔 👕 👖 👗 👜 👟 👑 💍 💎 📰 📅 📈 📊 📋 📓 📪 🧰 🧴 🚿 🚽 🛏 📲 ☎ ⏰ ⌛"),
        Cat("Symbols", "❤️", "✅ ❌ ❗ ❓ ❔ ❕ ‼️ ⁉️ ⚠️ 🚫 ⭕ 💯 ➕ ➖ ➗ ✖️ ♻️ 🔰 💠 🔴 🔵 🟠 🟡 🟢 🟣 🟤 🟥 🟦 🟧 🟨 🟩 🟪 🟫 🔺 🔻 🔼 🔽 ⬆️ ↗️ ➡️ ↘️ ⬇️ ↙️ ⬅️ ↖️ ↕️ ↔️ 🔀 🔁 🔂 ▶️ ⏩ ⏪ ♈ ♉ ♊ ♋ ♌ ♍ ♎ ♏ ♐ ♑ ♒ ♓ ✔️ ☑️ ✨ ©️ ®️ ™️ ♠️ ♥️ ♦️ ♣️ #️⃣ *️⃣ 0️⃣ 1️⃣ 2️⃣ 3️⃣ 4️⃣ 5️⃣ 6️⃣ 7️⃣ 8️⃣ 9️⃣ 🔟"),
        Cat("Flags", "🏳️", "🏁 🚩 🎌 🏳️ 🏴 🇧🇩 🇮🇳 🇵🇰 🇳🇵 🇱🇰 🇧🇹 🇲🇻 🇦🇫 🇺🇸 🇬🇧 🇨🇦 🇦🇺 🇳🇿 🇮🇪 🇫🇷 🇩🇪 🇮🇹 🇪🇸 🇵🇹 🇳🇱 🇧🇪 🇨🇭 🇦🇹 🇸🇪 🇳🇴 🇩🇰 🇫🇮 🇮🇸 🇵🇱 🇨🇿 🇬🇷 🇹🇷 🇷🇺 🇺🇦 🇸🇦 🇦🇪 🇶🇦 🇰🇼 🇴🇲 🇧🇭 🇮🇶 🇮🇷 🇮🇱 🇯🇴 🇱🇧 🇪🇬 🇲🇦 🇩🇿 🇹🇳 🇱🇾 🇳🇬 🇬🇭 🇰🇪 🇪🇹 🇿🇦 🇹🇿 🇺🇬 🇸🇩 🇸🇳 🇨🇳 🇯🇵 🇰🇷 🇰🇵 🇹🇼 🇭🇰 🇸🇬 🇲🇾 🇹🇭 🇻🇳 🇮🇩 🇵🇭 🇲🇲 🇰🇭 🇱🇦 🇲🇳 🇧🇷 🇦🇷 🇨🇱 🇨🇴 🇵🇪 🇻🇪 🇲🇽 🇨🇺 🇺🇾 🇪🇨 🇧🇴 🇵🇾 🇪🇺 🇺🇳")
    )

    private val paint = Paint()
    private val cache = HashMap<String, List<String>>()

    /** True if the phone font has a glyph for the whole emoji (a flag or keycap counts as one glyph). Main thread only. */
    fun canDraw(e: String): Boolean = try { paint.hasGlyph(e) } catch (_: Exception) { true }

    /** Drawable emojis of a category. Checked once, then cached. */
    fun list(c: Cat): List<String> = cache.getOrPut(c.name) { c.all.filter { canDraw(it) } }
}
