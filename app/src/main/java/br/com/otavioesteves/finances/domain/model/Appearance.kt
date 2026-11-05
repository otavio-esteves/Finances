package br.com.otavioesteves.finances.domain.model

enum class ThemeMode(val label: String) {
    DARK("Escuro"), LIGHT("Claro"), SYSTEM("Aparelho")
}

enum class ThemePalette(val label: String) {
    RED("Vermelho e laranja"), ORANGE("Laranja"), BLUE("Azul"), GREEN("Verde"), PURPLE("Roxo"),
    PINK("Rosa"), TEAL("Turquesa"), GOLD("Dourado"), MONOCHROME("Preto e branco")
}
