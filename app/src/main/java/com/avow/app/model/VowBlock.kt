package com.avow.app.model

data class VowBlock(
    val id: String,
    val name: String,
    val isEnabled: Boolean,
    val startHour: Int,
    val startMin: Int,
    val endHour: Int,
    val endMin: Int,
    val targetApps: Set<String>,
    val specificDomain: String
) {
    companion object {
        fun serializeList(blocks: List<VowBlock>): String {
            return blocks.joinToString(";") { block ->
                val appsStr = block.targetApps.sorted().joinToString(",")
                val sanitizedName = block.name.replace('=', ' ').replace(';', ' ')
                val sanitizedDomain = block.specificDomain.replace('=', ' ').replace(';', ' ')
                "${block.id}=${sanitizedName}=${block.isEnabled}=${block.startHour}=${block.startMin}=${block.endHour}=${block.endMin}=${appsStr}=${sanitizedDomain}"
            }
        }

        fun deserializeList(serialized: String?): List<VowBlock> {
            if (serialized.isNullOrEmpty()) return emptyList()
            return serialized.split(";").mapNotNull { blockStr ->
                if (blockStr.isEmpty()) return@mapNotNull null
                val parts = blockStr.split("=")
                if (parts.size >= 9) {
                    val id = parts[0]
                    val name = parts[1]
                    val isEnabled = parts[2].toBoolean()
                    val startHour = parts[3].toIntOrNull() ?: 0
                    val startMin = parts[4].toIntOrNull() ?: 0
                    val endHour = parts[5].toIntOrNull() ?: 0
                    val endMin = parts[6].toIntOrNull() ?: 0
                    val targetApps = if (parts[7].isEmpty()) emptySet() else parts[7].split(",").toSet()
                    val specificDomain = parts[8]
                    VowBlock(id, name, isEnabled, startHour, startMin, endHour, endMin, targetApps, specificDomain)
                } else if (parts.size == 8) {
                    val id = parts[0]
                    val name = parts[1]
                    val isEnabled = parts[2].toBoolean()
                    val startHour = parts[3].toIntOrNull() ?: 0
                    val startMin = parts[4].toIntOrNull() ?: 0
                    val endHour = parts[5].toIntOrNull() ?: 0
                    val endMin = parts[6].toIntOrNull() ?: 0
                    val targetApps = if (parts[7].isEmpty()) emptySet() else parts[7].split(",").toSet()
                    VowBlock(id, name, isEnabled, startHour, startMin, endHour, endMin, targetApps, "")
                } else {
                    null
                }
            }
        }
    }
}
