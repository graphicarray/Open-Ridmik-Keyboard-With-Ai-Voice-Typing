package com.graphicarray.voicetyper

import java.util.Locale

/**
 * Small built-in word lists so suggestions are not empty on day one.
 * Pure Kotlin (no Android classes). Words are listed most common first.
 * Phrases turn into word pairs: "let me know" = (let, me) + (me, know). "<s>" = start of a sentence.
 */
object Starter {
    private const val EN_WORDS = """
the be to of and a in that have i it for not on with he as you do at this but his by from they we say her she or an
will my one all would there their what so up out if about who get which go me when make can like time no just him
know take people into year your good some could them see other than then now look only come its over think also
back after use two how our work first well way even new want because any these give day most us is are was were
been has had did said does made find here thing many through where much should before right too mean old same
tell boy follow came show around form three small set put end why again turn off went number great men found
still between name home big own under read last never left along while might next below saw something thought
both few those always looked large often together asked house world going school important until food keep
children once took sometimes four head above kind began almost live page got earth need far hand high mother
light country father let night picture being study second soon story since white ever paper hard near better best
across during today however sure knew try told young sun whole hear example heard several change answer room sea
against top turned learn point city play toward five using himself usually
hello hi hey thanks thank please sorry okay ok yes yeah yep nope maybe fine nice awesome cool welcome bye goodbye
morning afternoon evening tonight tomorrow yesterday week weekend month monday tuesday wednesday thursday friday
saturday sunday meeting call message send sent email phone address location busy free available problem issue
question help love miss happy sad tired hungry ready done working coming doing trying looking waiting thinking
talking reading writing eating sleeping really actually probably definitely already anyway though although
perhaps very quite enough every everything everyone anything anyone someone nothing nobody later earlier early
late ago yet either neither each another such different
family friend friends brother sister mom dad wife husband son daughter baby man woman girl person team company
office project client customer business money price payment order delivery product service design logo print file
photo video image text document report plan idea list link website app account mobile internet online update
version download upload install open close start stop save delete check review confirm cancel share receive reply
forward
believe hold bring happen write provide sit stand lose pay meet include continue lead understand watch create
speak allow add spend grow walk win offer remember consider appear buy wait serve expect build stay fall cut reach
remain
i'm i'll i've i'd it's that's what's you're we're they're can't won't didn't doesn't don't isn't aren't wasn't
weren't wouldn't couldn't shouldn't haven't hasn't hadn't let's there's here's he's she's you'll we'll they'll
you've we've they've who's how's where's
"""

    private const val EN_PAIRS = """
<s> i | <s> the | <s> hi | <s> thanks | <s> what | <s> how | <s> please | <s> hello | <s> thank | <s> can |
<s> yes | <s> ok | <s> we | <s> it | <s> you | <s> if | <s> good | <s> sorry | <s> i'm | <s> hey | <s> this |
<s> there | <s> do | <s> are | <s> is |
i am | i will | i have | i can | i don't | i think | i want | i need | i love | i was | i would | i know | i just |
i hope | i'm going | i'm not | i'm sorry | i'm in | i'm on | i'll be | i'll send | i'll call | i'll let | i'll check |
i've been | i've got | i've sent |
thank you | thanks for | thanks again | thank you so much |
you are | you can | you have | you will | you know | you need | you want | you should | you too |
you're welcome | you're right | you're not |
how are you | how to | how much | how many | how do | how is | how about | how was | how can | how long |
are you | are there | are we | are they | are going |
what is | what are | what do | what time | what about | what happened | what's up | what's the |
is it | is there | is the | is this | is that | it is | it was | it will | it would | it's a | it's ok | it's not |
it's the | it's fine |
do you | do not | do it | do we | can you | can i | can we | can be | can't wait | can't find | can't make |
will be | will you | will do | will send | will call | will let | will see | will check |
please let me know | please send | please call | please check | please confirm | please find | let me | let us |
let's go | let's see | let's do | let's meet | see you | see the | see if | see how | get back | get the | get it |
go to | going to | want to | need to | have to | has to | had to | able to | try to | trying to |
looking for | looking forward to | waiting for | good morning | good afternoon | good evening | good night |
good luck | good day | good job | good idea | have a | have been | have you | have the | have no |
a lot | a bit | a few | a little | a good | a new | of the | of course | in the | in a | on the | to the | to be |
to do | to get | to see | to go | for the | for you | for me | for a | at the | at least | at home | at work |
with you | with the | with me | and the | and i | and then | but i | but the | if you | if i | if it | if there |
so much | so i | so we | so the | as soon as | as well | as a | as the | right now | right away | all the |
all of | all right | very much | very good | very well | next week | next month | next time | next day |
last week | last night | last time | this is | this week | this morning | this time | that is | that was |
that's great | that's right | that's fine | there is | there are | there was | there will | there's a |
there's no | we are | we have | we will | we can | we need | we should | we're going | we'll be |
they are | they have | they will | he is | she is | he was | she was | he said | she said |
my name | my phone | my number | my friend | your name | your phone | your number | your order | your time |
no problem | no worries | no need | not sure | not yet | not really | not at | not a | not the |
best regards | kind regards | best wishes | take care | talk to | talk soon | call me | call you | send me |
send you | tell me | ask me | give me | show me | text me | email me | let me know | know if | know that |
know what | know how
"""

    private const val BN_WORDS = """
আমি তুমি আপনি সে আমরা তোমরা তারা এই ওই সেই এটা ওটা সেটা এখানে সেখানে কোথায় কখন কেন কীভাবে কিভাবে কি কী কে
কাকে কত কোন কিছু সব সবাই অনেক একটু একটা একটি দুটি আরও আর এবং ও কিন্তু তবে তাহলে যদি অথবা বা কারণ তাই যে
যা যখন তখন এখন আজ কাল আগে পরে সাথে জন্য থেকে পর্যন্ত মধ্যে সঙ্গে দিয়ে করে করা করি করব করবো করছি করেছি
করুন করবেন করতে করেন করছেন করেছেন করছে করেছে হয় হবে হয়েছে হচ্ছে হয়ে হতে হই হন হলো হলে আছে আছি আছেন
আছো নেই নাই না নি হ্যাঁ ঠিক ভালো খারাপ সুন্দর বড় ছোট নতুন পুরনো ধন্যবাদ স্বাগতম দুঃখিত মাফ সালাম
আসসালামু আলাইকুম ওয়ালাইকুম আসসালাম শুভ সকাল দুপুর বিকেল সন্ধ্যা রাত দিন সময় সপ্তাহ মাস বছর ঘণ্টা মিনিট
আজকে কালকে গতকাল আগামীকাল যাব যাবো যাচ্ছি যাবেন যান যাওয়া এসো এসেছি আসব আসবো আসছি আসুন আসেন আসা বলুন
বলছি বলেছি বলব বলবো বলতে বলে বলা দেখুন দেখব দেখি দেখেছি দেখা জানি জানা জানাবেন জানাচ্ছি জানিয়ে জানতে
পারি পারব পারবো পারেন পারে পারবেন পারছি লাগবে লাগে চাই চাইছি চান দিন দিচ্ছি দেব দেবো দেবেন দিয়েছি নিন
নিয়ে নেব নেবো নিচ্ছি নিয়েছি খাব খেয়েছি খাওয়া ঘুম পড়া পড়ুন লেখা কথা কাজ কাজে বাড়ি বাসা ঘর অফিস স্কুল
কলেজ বাজার রাস্তা গাড়ি বাস টাকা দাম মূল্য ফোন মোবাইল নম্বর নাম ঠিকানা মেসেজ খবর তথ্য সমস্যা সাহায্য প্রশ্ন
উত্তর ভাই আপু ভাইয়া বন্ধু বাবা মা আব্বু আম্মু ছেলে মেয়ে বাচ্চা মানুষ লোক সবার সবাইকে বাংলাদেশ ঢাকা বাংলা
ইংরেজি দেশ শহর গ্রাম জীবন ভালোবাসা মন মনে খুব বেশি কম আরো প্রথম শেষ আবার শুধু শুধুমাত্র শুরু প্রয়োজন
দরকার অবশ্যই সম্ভব কিছুই কেউ কোনো কোনও এমন তেমন যেমন কেমন তোমার আপনার আমার তার তাদের আমাদের
তোমাদের আপনাদের আমাকে তোমাকে আপনাকে তাকে এর ওর যার কার প্লিজ হ্যালো ওকে দয়া করে
"""

    private const val BN_PAIRS = """
<s> আমি | <s> আপনি | <s> আসসালামু আলাইকুম | <s> ধন্যবাদ | <s> কেমন | <s> কি | <s> কী | <s> আজ | <s> এই |
<s> হ্যাঁ | <s> না | <s> ঠিক | <s> শুভ | <s> আপনার | <s> আমার | <s> তুমি |
ওয়ালাইকুম আসসালাম | শুভ সকাল | শুভ রাত্রি | শুভ সন্ধ্যা | শুভ জন্মদিন |
কেমন আছেন | কেমন আছো | আপনি কেমন | তুমি কেমন | ভালো আছি | ভালো আছেন | ভালো আছো | আমি ভালো আছি |
ধন্যবাদ আপনাকে | অনেক ধন্যবাদ | ঠিক আছে | ঠিক আছি | হ্যাঁ ঠিক | জানি না | পারব না | পারি না | করব না |
যাব না | আমি জানি | আমি যাব | আমি আসব | আমি করব | আমি পারব | আমি চাই | আমি আছি | আমি এখন | আমি আসছি |
আমি যাচ্ছি | আমি বলছি | আমার নাম | আপনার নাম | আপনার সাথে | আমার সাথে | কী খবর | কি খবর | কি করছেন |
কি করছো | কী করছেন | কি হয়েছে | কী হয়েছে | কোথায় আছেন | কোথায় যাবেন | কখন আসবেন | কখন যাবেন | কত টাকা |
অনেক ভালো | খুব ভালো | খুব সুন্দর | খুব দরকার | আজ রাতে | কাল সকালে | এই বিষয়ে | এই কাজ | এই জন্য |
সেই জন্য | এর জন্য | আপনার জন্য | আমার জন্য | এখন আমি | পরে কথা | পরে জানাবেন | পরে বলব | কথা হবে |
দেখা হবে | দয়া করে জানাবেন | দেখা করব | কাজ করছি | কাজ করছেন | কাজ শেষ | কাজ আছে | বাসায় আছি |
বাড়ি যাচ্ছি | অফিসে আছি | অফিসে যাচ্ছি | ফোন করুন | ফোন করব | মেসেজ করুন | মেসেজ দিন | টাকা পাঠান |
টাকা পাঠিয়েছি | নম্বর দিন | ঠিকানা দিন | জানাবেন প্লিজ
"""

    /** Word -> starter strength 1..7 (7 = most common). */
    fun uni(bn: Boolean): Map<String, Int> = if (bn) bnData.first else enData.first

    /** "prev\tnext" -> starter strength 1..7. */
    fun bi(bn: Boolean): Map<String, Int> = if (bn) bnData.second else enData.second

    private val enData by lazy { build(EN_WORDS, EN_PAIRS, false) }
    private val bnData by lazy { build(BN_WORDS, BN_PAIRS, true) }

    private fun fix(w: String, bn: Boolean): String =
        if (bn) Tok.canonBn(w) else w.lowercase(Locale.ROOT)

    private fun build(words: String, pairs: String, bn: Boolean): Pair<HashMap<String, Int>, HashMap<String, Int>> {
        val uni = HashMap<String, Int>()
        var idx = 0
        for (raw in words.split(Regex("\\s+"))) {
            if (raw.isEmpty()) continue
            val w = fix(raw, bn)
            if (uni.containsKey(w)) continue
            uni[w] = 7 - minOf(6, idx / 120)
            idx++
        }
        val bi = HashMap<String, Int>()
        val perPrev = HashMap<String, Int>()
        for (phrase in pairs.split('|')) {
            val t = phrase.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.map { fix(it, bn) }
            for (i in 0 until t.size - 1) {
                val a = t[i]
                val b = t[i + 1]
                if (b == Tok.START) continue
                val key = a + "\t" + b
                if (bi.containsKey(key)) continue
                val n = perPrev[a] ?: 0
                perPrev[a] = n + 1
                bi[key] = maxOf(1, 7 - n)
                if (!uni.containsKey(b)) uni[b] = 1
            }
        }
        return Pair(uni, bi)
    }
}
