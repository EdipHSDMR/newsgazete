package com.example.data

data class TurkishCity(
    val name: String,
    val districts: List<String>,
)

object TurkishLocations {
    val cities: List<TurkishCity> = listOf(
        TurkishCity(
            name = "İstanbul",
            districts = listOf("Kadıköy", "Beşiktaş", "Şişli", "Üsküdar", "Bakırköy", "Beyoğlu", "Sarıyer", "Fatih", "Ataşehir", "Maltepe", "Kartal", "Pendik", "Ümraniye", "Başakşehir", "Beylikdüzü", "Esenyurt", "Tuzla", "Zeytinburnu", "Eyüpsultan", "Adalar")
        ),
        TurkishCity(
            name = "Ankara",
            districts = listOf("Çankaya", "Keçiören", "Yenimahalle", "Mamak", "Etimesgut", "Sincan", "Altındağ", "Gölbaşı", "Pursaklar", "Polatlı", "Beypazarı")
        ),
        TurkishCity(
            name = "İzmir",
            districts = listOf("Konak", "Karşıyaka", "Bornova", "Buca", "Alsancak", "Çeşme", "Urla", "Karabağlar", "Menemen", "Torbalı", "Gaziemir", "Çiğli", "Foça", "Seferihisar")
        ),
        TurkishCity(
            name = "Bursa",
            districts = listOf("Osmangazi", "Nilüfer", "Yıldırım", "İnegöl", "Mudanya", "Gemlik", "Gürsu", "Kestel")
        ),
        TurkishCity(
            name = "Antalya",
            districts = listOf("Muratpaşa", "Konyaaltı", "Kepez", "Alanya", "Manavgat", "Kaş", "Kemer", "Serik", "Kumluca", "Gazipaşa")
        ),
        TurkishCity(
            name = "Adana",
            districts = listOf("Seyhan", "Çukurova", "Yüreğir", "Sarıçam", "Ceyhan", "Kozan")
        ),
        TurkishCity(
            name = "Trabzon",
            districts = listOf("Ortahisar", "Akçaabat", "Yomra", "Of", "Araklı", "Vakfıkebir", "Sürmene", "Maçka")
        ),
        TurkishCity(
            name = "Konya",
            districts = listOf("Selçuklu", "Meram", "Karatay", "Ereğli", "Akşehir", "Beyşehir")
        ),
        TurkishCity(
            name = "Gaziantep",
            districts = listOf("Şahinbey", "Şehitkamil", "Nizip", "İslahiye", "Oğuzeli")
        ),
        TurkishCity(
            name = "Eskişehir",
            districts = listOf("Tepebaşı", "Odunpazarı", "Sivrihisar", "Çifteler")
        ),
        TurkishCity(
            name = "Diyarbakır",
            districts = listOf("Bağlar", "Kayapınar", "Sur", "Yenişehir", "Bismil", "Ergani")
        ),
        TurkishCity(
            name = "Kayseri",
            districts = listOf("Melikgazi", "Kocasinan", "Talas", "Develi", "Yahyalı")
        ),
        TurkishCity(
            name = "Samsun",
            districts = listOf("İlkadım", "Atakum", "Canik", "Bafra", "Çarşamba", "Tekkeköy")
        ),
        TurkishCity(
            name = "Mersin",
            districts = listOf("Yenişehir", "Mezitli", "Toroslar", "Akdeniz", "Tarsus", "Erdemli", "Silifke")
        ),
        TurkishCity(
            name = "Kocaeli",
            districts = listOf("İzmit", "Gebze", "Darıca", "Körfez", "Gölcük", "Kartepe", "Başiskele")
        ),
        TurkishCity(
            name = "Muğla",
            districts = listOf("Bodrum", "Fethiye", "Marmaris", "Menteşe", "Milas", "Datça", "Ortaca")
        ),
        TurkishCity(
            name = "Aydın",
            districts = listOf("Efeler", "Kuşadası", "Didim", "Söke", "Nazilli", "İncirliova")
        ),
        TurkishCity(
            name = "Balıkesir",
            districts = listOf("Karesi", "Altıeylül", "Edremit", "Bandırma", "Ayvalık", "Burhaniye", "Gönen")
        ),
        TurkishCity(
            name = "Denizli",
            districts = listOf("Pamukkale", "Merkezefendi", "Çivril", "Acıpayam", "Tavas")
        ),
        TurkishCity(
            name = "Tekirdağ",
            districts = listOf("Süleymanpaşa", "Çorlu", "Çerkezköy", "Kapaklı", "Ergene")
        ),
        TurkishCity(
            name = "Şanlıurfa",
            districts = listOf("Haliliye", "Eyyübiye", "Karaköprü", "Siverek", "Viranşehir")
        ),
        TurkishCity(
            name = "Manisa",
            districts = listOf("Yunusemre", "Şehzadeler", "Akhisar", "Turgutlu", "Salihli", "Soma")
        ),
        TurkishCity(
            name = "Hatay",
            districts = listOf("Antakya", "İskenderun", "Defne", "Dörtyol", "Samandağ", "Kırıkhan")
        ),
        TurkishCity(
            name = "Çanakkale",
            districts = listOf("Merkez", "Biga", "Çan", "Gelibolu", "Ayvacık", "Ezine", "Bozcaada")
        ),
        TurkishCity(
            name = "Rize",
            districts = listOf("Merkez", "Çayeli", "Ardeşen", "Pazar", "Fındıklı", "Çamlıhemşin")
        ),
        TurkishCity(
            name = "Sakarya",
            districts = listOf("Adapazarı", "Serdivan", "Erenler", "Akyazı", "Hendek", "Sapanca")
        ),
        TurkishCity(
            name = "Malatya",
            districts = listOf("Battalgazi", "Yeşilyurt", "Doğanşehir", "Akçadağ")
        ),
        TurkishCity(
            name = "Erzurum",
            districts = listOf("Yakutiye", "Palandöken", "Aziziye", "Oltu", "Pasinler")
        ),
        TurkishCity(
            name = "Van",
            districts = listOf("İpekyolu", "Tuşba", "Edremit", "Erciş")
        ),
        TurkishCity(
            name = "Sivas",
            districts = listOf("Merkez", "Şarkışla", "Yıldızeli", "Suşehri")
        ),
    )

    fun searchLocations(query: String): List<Pair<String, String?>> {
        val q = query.trim().lowercase()
        if (q.isBlank()) {
            return cities.map { it.name to null }
        }

        val results = mutableListOf<Pair<String, String?>>()
        cities.forEach { city ->
            if (city.name.lowercase().contains(q)) {
                results.add(city.name to null)
            }
            city.districts.forEach { district ->
                if (district.lowercase().contains(q) || "${district.lowercase()} ${city.name.lowercase()}".contains(q)) {
                    results.add(city.name to district)
                }
            }
        }
        return results.take(30)
    }

    fun getDistrictsForCity(cityName: String): List<String> {
        val found = cities.firstOrNull { it.name.equals(cityName, ignoreCase = true) }
        return found?.districts ?: listOf("Merkez", "Kuzey", "Güney", "Doğu", "Batı")
    }
}

