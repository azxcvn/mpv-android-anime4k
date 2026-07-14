package com.fam4k007.videoplayer.utils

/**
 * 自然排序比较器工具类
 *
 * 支持字符串中数字的正确排序，例如：
 * - "第1集" < "第2集" < "第10集" < "第11集"
 * - "1.mp4" < "2.mp4" < "10.mp4"
 * - "folder1" < "folder2" < "folder10"
 *
 * 而不会出现字典序的 1, 10, 11, 2, 3 这样的错误顺序。
 */
object NaturalOrderComparator {

    /**
     * 自然排序字符串比较
     * 将字符串中的数字段按数值大小比较，非数字段按字符顺序比较
     */
    fun compare(str1: String, str2: String): Int {
        val s1 = str1.lowercase()
        val s2 = str2.lowercase()

        var i1 = 0
        var i2 = 0

        while (i1 < s1.length && i2 < s2.length) {
            val c1 = s1[i1]
            val c2 = s2[i2]

            // 如果两个字符都是数字，则提取完整的数字进行比较
            if (c1.isDigit() && c2.isDigit()) {
                // 提取第一个数字
                var num1 = 0
                while (i1 < s1.length && s1[i1].isDigit()) {
                    num1 = num1 * 10 + (s1[i1] - '0')
                    i1++
                }

                // 提取第二个数字
                var num2 = 0
                while (i2 < s2.length && s2[i2].isDigit()) {
                    num2 = num2 * 10 + (s2[i2] - '0')
                    i2++
                }

                // 比较数字大小
                if (num1 != num2) {
                    return num1 - num2
                }
            } else {
                // 普通字符比较
                if (c1 != c2) {
                    return c1 - c2
                }
                i1++
                i2++
            }
        }

        return s1.length - s2.length
    }

    /**
     * 创建自然排序比较器
     * @param selector 从对象中提取排序键的函数
     * @return 自然排序的 Comparator
     */
    fun <T> comparator(selector: (T) -> String): Comparator<T> {
        return Comparator { a, b -> compare(selector(a), selector(b)) }
    }
}
