package com.pixiv.downloader.util

import java.util.regex.Pattern

object PixivUrlParser {

    private val ARTWORKS_PATTERN = Pattern.compile("(?:artworks/|illust_id=|/i/)(\\d+)", Pattern.CASE_INSENSITIVE)
    private val PURE_NUMBER_PATTERN = Pattern.compile("^\\s*(\\d{6,12})\\s*$")

    /**
     * 从输入的任意字符串中提取单个 Pixiv 作品 ID
     */
    fun parseSingleId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        val pureMatcher = PURE_NUMBER_PATTERN.matcher(trimmed)
        if (pureMatcher.matches()) {
            return pureMatcher.group(1)
        }

        val matcher = ARTWORKS_PATTERN.matcher(trimmed)
        if (matcher.find()) {
            return matcher.group(1)
        }

        return null
    }

    /**
     * 从多行或混合文本中提取所有 Pixiv 作品 ID（去重保序）
     */
    fun parseMultipleIds(input: String): List<String> {
        val result = LinkedHashSet<String>()
        val lines = input.split(Regex("[\\r\\n,;]+"))
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            // 检查纯数字
            val pureMatcher = PURE_NUMBER_PATTERN.matcher(trimmed)
            if (pureMatcher.matches()) {
                pureMatcher.group(1)?.let { result.add(it) }
                continue
            }

            // 检查 URL 正则
            val matcher = ARTWORKS_PATTERN.matcher(trimmed)
            while (matcher.find()) {
                matcher.group(1)?.let { result.add(it) }
            }
        }
        return result.toList()
    }
}
