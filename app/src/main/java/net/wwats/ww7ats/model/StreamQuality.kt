package net.wwats.ww7ats.model

enum class StreamQuality(
    val label: String,
    val width: Int,
    val height: Int,
    val bitrate: Int,
    val fps: Int
) {
    LOW("480p Low", 854, 480, 800_000, 30),
    MEDIUM("720p Medium", 1280, 720, 1_500_000, 30),
    HIGH("1080p High", 1920, 1080, 2_500_000, 30);

    companion object {
        fun fromName(name: String): StreamQuality =
            entries.find { it.name == name } ?: MEDIUM
    }
}
