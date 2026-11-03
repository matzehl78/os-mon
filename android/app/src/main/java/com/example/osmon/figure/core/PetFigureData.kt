// PetFigureData.kt: GENERIERT von gen_figures.mjs aus osmon-prototyp.html. Nicht von Hand ändern.

package com.example.osmon.figure

val ALL_FIGURES: List<PetFigure> = listOf(
    PetFigure(
        id = "lumo", name = "Lumo",
        colors = FigureColors(
            body = 0xFF4FD9F7.toInt(), bodySick = 0xFF8CD6E7.toInt(), ant = 0xFFFF4FD8.toInt(), off = 0xFF763283.toInt(),
            heart = 0xFFFF4FD8.toInt(), iris = 0xFF8A5CFF.toInt(), lid = 0xFFAD8DFF.toInt(), line = 0xFF1B1E4B.toInt(),
            cheek = 0xFFFF7FC8.toInt(), tongue = 0xFFFF8FD6.toInt(), sweat = 0xFF9FF0FF.toInt()
        ),
        shape = FigureShape(
            lumo = true,
            path = "M72 138 Q52 168 66 194 Q110 208 154 194 Q168 168 148 138 Z",
            head = Ellipse(110f, 96f, 64f, 58f),
            face = Face(100f, 26f, 21f, 123f),
            ant = Antenna(24f, 40f, 1f, listOf(Bead(0f, 0f, 8f), Bead(6f, -16f, 10f), Bead(14f, -37f, 14f))),
            heart = HeartSpot(110f, 172f, 19f),
            tail = Pt(140f, 190f),
            arms = listOf(Arm(62f, 164f, 10f, 20f, -12f), Arm(158f, 164f, 10f, 20f, 12f)),
            feet = listOf(Foot(80f, 192f, 24f, 18f), Foot(116f, 192f, 24f, 18f)),
            glint = Glint(78f, 62f, -28f),
            sweat = Pt(174f, 54f),
            zz = Pt(14f, 44f)
        )
    ),
    PetFigure(
        id = "minto", name = "Minto",
        colors = FigureColors(
            body = 0xFFACFDEC.toInt(), bodySick = 0xFFBBE8E1.toInt(), ant = 0xFFFE9588.toInt(), off = 0xFF764E63.toInt(),
            heart = 0xFFFE9588.toInt(), iris = 0xFF03A294.toInt(), lid = 0xFFACFDEC.toInt(), line = 0xFF080D30.toInt(),
            cheek = 0xFFFE9588.toInt(), tongue = 0xFFFF8FD6.toInt(), sweat = 0xFF9FF0FF.toInt()
        ),
        shape = FigureShape(
            lumo = false,
            path = "M37 120 A73 73 0 1 1 183 120 A73 73 0 1 1 37 120 Z",
            head = null,
            face = Face(111f, 39f, 22f, 135f),
            ant = Antenna(33f, 47f, 1f, listOf(Bead(0f, 0f, 11f), Bead(12f, -17f, 13f), Bead(31f, -39f, 20f))),
            heart = HeartSpot(110f, 168f, 20f),
            tail = Pt(152f, 186f),
            arms = listOf(Arm(40f, 158f, 10f, 13f, 30f), Arm(180f, 158f, 10f, 13f, -30f)),
            feet = listOf(Foot(54f, 193f, 43f, 17f), Foot(124f, 193f, 43f, 17f)),
            glint = Glint(75f, 64f, -30f),
            sweat = Pt(172f, 66f),
            zz = Pt(14f, 44f)
        )
    ),
    PetFigure(
        id = "lila", name = "Lila",
        colors = FigureColors(
            body = 0xFFDBBDFD.toInt(), bodySick = 0xFFD2C8EA.toInt(), ant = 0xFFFEF55A.toInt(), off = 0xFF767451.toInt(),
            heart = 0xFFFBE93E.toInt(), iris = 0xFF7138C8.toInt(), lid = 0xFFDBBDFD.toInt(), line = 0xFF080D30.toInt(),
            cheek = 0xFFFE9588.toInt(), tongue = 0xFFFF8FD6.toInt(), sweat = 0xFF9FF0FF.toInt()
        ),
        shape = FigureShape(
            lumo = false,
            path = "M92 100 C95 112 90 122 86 134 C80 150 78 168 84 182 C90 196 104 199 110 199 C116 199 130 196 136 182 C142 168 140 150 134 134 C130 122 125 112 128 100 Z",
            head = Ellipse(110f, 70f, 43f, 39f),
            face = Face(75f, 20f, 16f, 93f),
            ant = Antenna(27f, 36f, 1f, listOf(Bead(0f, 0f, 8f), Bead(7f, -17f, 10f), Bead(-4f, -38f, 13f))),
            heart = HeartSpot(110f, 128f, 14f),
            tail = Pt(130f, 192f),
            arms = listOf(Arm(74f, 140f, 9f, 33f, 20f), Arm(146f, 140f, 9f, 33f, -20f)),
            feet = listOf(Foot(72f, 197f, 35f, 13f), Foot(113f, 197f, 35f, 13f)),
            glint = Glint(88f, 48f, -30f),
            sweat = Pt(152f, 42f),
            zz = Pt(14f, 44f)
        )
    ),
    PetFigure(
        id = "glut", name = "Glut",
        colors = FigureColors(
            body = 0xFFFD827B.toInt(), bodySick = 0xFFE3AAA9.toInt(), ant = 0xFF01E3F3.toInt(), off = 0xFF116D8E.toInt(),
            heart = 0xFF01E3F3.toInt(), iris = 0xFF087DCE.toInt(), lid = 0xFFFD827B.toInt(), line = 0xFF080D30.toInt(),
            cheek = 0xFFE8456B.toInt(), tongue = 0xFFFF8FD6.toInt(), sweat = 0xFF9FF0FF.toInt()
        ),
        shape = FigureShape(
            lumo = false,
            path = "M110 52 C150 52 178 80 190 112 C200 140 198 172 184 190 L36 190 C22 172 20 140 30 112 C42 80 70 52 110 52 Z",
            head = null,
            face = Face(115f, 41f, 25f, 130f),
            ant = Antenna(38f, 52f, 1f, listOf(Bead(0f, 0f, 10f), Bead(4f, -19f, 12f), Bead(19f, -45f, 20f))),
            heart = HeartSpot(110f, 163f, 21f),
            tail = Pt(178f, 186f),
            arms = listOf(Arm(12f, 142f, 15f, 32f, 18f), Arm(208f, 142f, 15f, 32f, -18f)),
            feet = listOf(Foot(38f, 190f, 66f, 20f), Foot(116f, 190f, 66f, 20f)),
            glint = Glint(62f, 88f, -35f),
            sweat = Pt(194f, 78f),
            zz = Pt(14f, 44f)
        )
    ),
    PetFigure(
        id = "kasto", name = "Kasto",
        colors = FigureColors(
            body = 0xFF7DD6FE.toInt(), bodySick = 0xFFA3D4EA.toInt(), ant = 0xFFFEB52D.toInt(), off = 0xFF765A3F.toInt(),
            heart = 0xFFFDB532.toInt(), iris = 0xFF3240D5.toInt(), lid = 0xFF7DD6FE.toInt(), line = 0xFF080D30.toInt(),
            cheek = 0xFFFE9588.toInt(), tongue = 0xFFFF8FD6.toInt(), sweat = 0xFF9FF0FF.toInt()
        ),
        shape = FigureShape(
            lumo = false,
            path = "M88 55 H132 Q157 55 157 80 V164 Q157 186 135 186 H85 Q63 186 63 164 V80 Q63 55 88 55 Z",
            head = null,
            face = Face(102f, 25f, 18f, 116f),
            ant = Antenna(22f, 52f, 1f, listOf(Bead(0f, 0f, 9f), Bead(6f, -13f, 11f), Bead(13f, -34f, 17f))),
            heart = HeartSpot(110f, 145f, 16f),
            tail = Pt(146f, 177f),
            arms = listOf(Arm(59f, 140f, 10f, 18f, 15f), Arm(161f, 140f, 10f, 18f, -15f)),
            feet = listOf(Foot(70f, 183f, 32f, 19f), Foot(118f, 183f, 32f, 19f)),
            glint = Glint(76f, 72f, -20f),
            sweat = Pt(170f, 56f),
            zz = Pt(14f, 44f)
        )
    ),
    PetFigure(
        id = "tropfi", name = "Tropfi",
        colors = FigureColors(
            body = 0xFFFEB5E2.toInt(), bodySick = 0xFFE4C4DC.toInt(), ant = 0xFFC475FB.toInt(), off = 0xFF5F4191.toInt(),
            heart = 0xFFBF74F1.toInt(), iris = 0xFF9035BD.toInt(), lid = 0xFFFEB5E2.toInt(), line = 0xFF080D30.toInt(),
            cheek = 0xFFFE9588.toInt(), tongue = 0xFFFF8FD6.toInt(), sweat = 0xFF9FF0FF.toInt()
        ),
        shape = FigureShape(
            lumo = false,
            path = "M110 43 C122 43 150 70 158 92 C164 110 152 120 141 125 C148 140 146 160 144 170 C140 186 120 187 110 187 C100 187 80 186 76 170 C74 160 72 140 79 125 C68 120 56 110 62 92 C70 70 98 43 110 43 Z",
            head = null,
            face = Face(96f, 25f, 15f, 110f),
            ant = Antenna(13f, 42f, 1f, listOf(Bead(0f, 0f, 8f), Bead(5f, -13f, 10f), Bead(15f, -30f, 15f))),
            heart = HeartSpot(110f, 146f, 16f),
            tail = Pt(138f, 178f),
            arms = listOf(Arm(70f, 151f, 10f, 19f, 18f), Arm(150f, 151f, 10f, 19f, -18f)),
            feet = listOf(Foot(73f, 183f, 34f, 19f), Foot(113f, 183f, 34f, 19f)),
            glint = Glint(80f, 78f, -35f),
            sweat = Pt(160f, 58f),
            zz = Pt(14f, 44f)
        )
    ),
    PetFigure(
        id = "wolki", name = "Wolki",
        colors = FigureColors(
            body = 0xFFFEF8E3.toInt(), bodySick = 0xFFE4E5DD.toInt(), ant = 0xFF01DDA0.toInt(), off = 0xFF116A6D.toInt(),
            heart = 0xFF03D89E.toInt(), iris = 0xFF04927D.toInt(), lid = 0xFFFEF8E3.toInt(), line = 0xFF080D30.toInt(),
            cheek = 0xFFFE9588.toInt(), tongue = 0xFFFF8FD6.toInt(), sweat = 0xFF9FF0FF.toInt()
        ),
        shape = FigureShape(
            lumo = false,
            path = "M110 50 C138 50 159 70 159 95 C159 113 148 124 134 127 C141 140 144 155 144 168 C144 180 138 185 128 185 L92 185 C82 185 76 180 76 168 C76 155 79 140 86 127 C72 124 61 113 61 95 C61 70 82 50 110 50 Z",
            head = null,
            face = Face(98f, 24f, 17f, 112f),
            ant = Antenna(14f, 46f, 1f, listOf(Bead(0f, 0f, 8f), Bead(7f, -13f, 10f), Bead(17f, -32f, 16f))),
            heart = HeartSpot(110f, 146f, 17f),
            tail = Pt(137f, 176f),
            arms = listOf(Arm(67f, 152f, 11f, 20f, 20f), Arm(153f, 152f, 11f, 20f, -20f)),
            feet = listOf(Foot(70f, 182f, 37f, 19f), Foot(112f, 182f, 37f, 19f)),
            glint = Glint(80f, 70f, -30f),
            sweat = Pt(165f, 56f),
            zz = Pt(14f, 44f)
        )
    ),
    PetFigure(
        id = "rauti", name = "Rauti",
        colors = FigureColors(
            body = 0xFFA165F9.toInt(), bodySick = 0xFFB59CE8.toInt(), ant = 0xFF0BDDFE.toInt(), off = 0xFF156A93.toInt(),
            heart = 0xFF06F1F7.toInt(), iris = 0xFF0596C8.toInt(), lid = 0xFFA165F9.toInt(), line = 0xFF080D30.toInt(),
            cheek = 0xFFFE9588.toInt(), tongue = 0xFFFF8FD6.toInt(), sweat = 0xFF9FF0FF.toInt()
        ),
        shape = FigureShape(
            lumo = false,
            path = "M110 49 C116 49 120 52 126 58 L162 98 C168 105 168 115 162 122 L149 135 C150 150 150 162 146 172 C140 184 124 188 110 188 C96 188 80 184 74 172 C70 162 70 150 71 135 L58 122 C52 115 52 105 58 98 L94 58 C100 52 104 49 110 49 Z",
            head = null,
            face = Face(109f, 27f, 15f, 121f),
            ant = Antenna(16f, 50f, 1f, listOf(Bead(0f, 0f, 9f), Bead(7f, -14f, 10f), Bead(16f, -33f, 15f))),
            heart = HeartSpot(110f, 155f, 17f),
            tail = Pt(130f, 172f),
            arms = listOf(Arm(64f, 152f, 10f, 16f, 15f), Arm(156f, 152f, 10f, 16f, -15f)),
            feet = listOf(Foot(77f, 182f, 32f, 17f), Foot(113f, 182f, 32f, 17f)),
            glint = Glint(84f, 76f, -45f),
            sweat = Pt(166f, 70f),
            zz = Pt(14f, 44f)
        )
    ),
    PetFigure(
        id = "sunni", name = "Sunni",
        colors = FigureColors(
            body = 0xFFFDD845.toInt(), bodySick = 0xFFE3D58E.toInt(), ant = 0xFFFD4DB1.toInt(), off = 0xFF753174.toInt(),
            heart = 0xFFFD5CC1.toInt(), iris = 0xFF9636C5.toInt(), lid = 0xFFFDD845.toInt(), line = 0xFF080D30.toInt(),
            cheek = 0xFFFE9588.toInt(), tongue = 0xFFFF8FD6.toInt(), sweat = 0xFF9FF0FF.toInt()
        ),
        shape = FigureShape(
            lumo = false,
            path = "M110 50 C136 50 154 70 154 95 C154 108 146 116 143 123 C148 135 150 145 148 160 C146 176 136 185 122 185 L98 185 C84 185 74 176 72 160 C70 145 72 135 77 123 C74 116 66 108 66 95 C66 70 84 50 110 50 Z",
            head = null,
            face = Face(97f, 21f, 14f, 110f),
            ant = Antenna(14f, 46f, 1f, listOf(Bead(0f, 0f, 8f), Bead(7f, -15f, 10f), Bead(15f, -34f, 16f))),
            heart = HeartSpot(110f, 145f, 17f),
            tail = Pt(138f, 176f),
            arms = listOf(Arm(62f, 146f, 11f, 18f, 20f), Arm(158f, 146f, 11f, 18f, -20f)),
            feet = listOf(Foot(70f, 183f, 35f, 17f), Foot(111f, 183f, 35f, 17f)),
            glint = Glint(82f, 70f, -30f),
            sweat = Pt(160f, 56f),
            zz = Pt(14f, 44f)
        )
    ),
    PetFigure(
        id = "orbo", name = "Orbo",
        colors = FigureColors(
            body = 0xFFFF985D.toInt(), bodySick = 0xFFE4B59A.toInt(), ant = 0xFF356AFD.toInt(), off = 0xFF253C92.toInt(),
            heart = 0xFF286BF8.toInt(), iris = 0xFF087DCE.toInt(), lid = 0xFFFF985D.toInt(), line = 0xFF080D30.toInt(),
            cheek = 0xFFFE9588.toInt(), tongue = 0xFFFF8FD6.toInt(), sweat = 0xFF9FF0FF.toInt()
        ),
        shape = FigureShape(
            lumo = false,
            path = "M110 49 C134 49 153 64 153 85 C153 101 144 111 134 116 C145 124 149 138 149 152 C149 171 133 183 110 183 C87 183 71 171 71 152 C71 138 75 124 86 116 C76 111 67 101 67 85 C67 64 86 49 110 49 Z",
            head = null,
            face = Face(90f, 20f, 14f, 104f),
            ant = Antenna(16f, 45f, 1f, listOf(Bead(0f, 0f, 8f), Bead(7f, -13f, 10f), Bead(15f, -32f, 15f))),
            heart = HeartSpot(110f, 146f, 17f),
            tail = Pt(140f, 176f),
            arms = listOf(Arm(61f, 149f, 11f, 19f, 18f), Arm(159f, 149f, 11f, 19f, -18f)),
            feet = listOf(Foot(66f, 180f, 40f, 18f), Foot(114f, 180f, 40f, 18f)),
            glint = Glint(82f, 66f, -30f),
            sweat = Pt(160f, 54f),
            zz = Pt(14f, 44f)
        )
    ),
    PetFigure(
        id = "schnecki", name = "Schnecki",
        colors = FigureColors(
            body = 0xFF5E6076.toInt(), bodySick = 0xFF9499A6.toInt(), ant = 0xFF98E33C.toInt(), off = 0xFF4D6D45.toInt(),
            heart = 0xFF98E33C.toInt(), iris = 0xFF4E8A12.toInt(), lid = 0xFF5E6076.toInt(), line = 0xFF080D30.toInt(),
            cheek = 0xFFFF7A9A.toInt(), tongue = 0xFFFF8FD6.toInt(), sweat = 0xFF9FF0FF.toInt()
        ),
        shape = FigureShape(
            lumo = false,
            path = "M110 65 C138 65 158 76 159 98 C160 104 160 106 161 112 C163 122 158 128 153 133 C157 143 157 152 156 162 C155 178 140 187 122 187 L98 187 C80 187 65 178 64 162 C63 152 63 143 67 133 C62 128 57 122 59 112 C60 106 60 104 61 98 C62 76 82 65 110 65 Z",
            head = null,
            face = Face(110f, 26f, 14f, 122f),
            ant = Antenna(19f, 64f, 1f, listOf(Bead(0f, 0f, 8f), Bead(7f, -15f, 9f), Bead(14f, -34f, 14f))),
            heart = HeartSpot(110f, 158f, 17f),
            tail = Pt(149f, 177f),
            arms = listOf(Arm(58f, 158f, 12f, 18f, 25f), Arm(162f, 158f, 12f, 18f, -25f)),
            feet = listOf(Foot(66f, 182f, 38f, 17f), Foot(113f, 182f, 38f, 17f)),
            glint = Glint(76f, 80f, -25f),
            sweat = Pt(170f, 74f),
            zz = Pt(14f, 44f)
        )
    ),
)
