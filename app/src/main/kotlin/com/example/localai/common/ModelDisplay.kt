package com.example.localai.common

/** 列表标题只显示模型名称；量化规格继续在副信息和详情中展示。 */
object ModelDisplay {
    fun name(fullName: String): String = fullName.substringBefore(" · ").removeSuffix("-Instruct")
}
