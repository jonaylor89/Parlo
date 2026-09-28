package com.parlo.app.model

/** Where a language is mostly spoken; used to group the picker. */
enum class Region(val label: String) {
    EUROPE("Europe"),
    ASIA("East & Southeast Asia"),
    SOUTH_ASIA("South Asia"),
    MIDDLE_EAST("Middle East & Central Asia"),
    AFRICA("Africa"),
    AMERICAS("Americas"),
    OCEANIA("Oceania"),
}

/** A regional accent / dialect of a [Language]. [name] is what the tutor is told to speak. */
data class Dialect(
    val name: String,
    val region: String,
    val flag: String,
)

data class Language(
    val name: String,
    val nativeName: String,
    val flag: String,
    val region: Region,
    val dialects: List<Dialect>,
    /** Rough number of speakers, used for the "Popular" section and default ordering. */
    val speakersMillions: Int,
) {
    val defaultDialect: Dialect get() = dialects.first()
}

/**
 * Every language Parlo knows about, with every regional accent worth asking a tutor for.
 * Free text is always accepted on top of this; the catalog is a map, not a fence.
 */
object LanguageCatalog {
    private fun d(name: String, region: String, flag: String) = Dialect(name, region, flag)

    val languages: List<Language> = listOf(
        // ───────────── Europe ─────────────
        Language(
            "Spanish", "Español", "🇪🇸", Region.EUROPE, speakersMillions = 560,
            dialects = listOf(
                d("Castilian Spanish (Madrid)", "Central Spain", "🇪🇸"),
                d("Mexican Spanish (Mexico City)", "Mexico", "🇲🇽"),
                d("Rioplatense Spanish (Buenos Aires)", "Argentina · Uruguay", "🇦🇷"),
                d("Colombian Spanish (Bogotá)", "Colombia", "🇨🇴"),
                d("Caribbean Spanish (Havana)", "Cuba", "🇨🇺"),
                d("Puerto Rican Spanish", "Puerto Rico", "🇵🇷"),
                d("Dominican Spanish", "Dominican Republic", "🇩🇴"),
                d("Peruvian Spanish (Lima)", "Peru", "🇵🇪"),
                d("Chilean Spanish (Santiago)", "Chile", "🇨🇱"),
                d("Venezuelan Spanish (Caracas)", "Venezuela", "🇻🇪"),
                d("Ecuadorian Spanish (Quito)", "Ecuador", "🇪🇨"),
                d("Bolivian Spanish", "Bolivia", "🇧🇴"),
                d("Paraguayan Spanish", "Paraguay", "🇵🇾"),
                d("Central American Spanish (Guatemala)", "Guatemala · Honduras · El Salvador", "🇬🇹"),
                d("Costa Rican Spanish", "Costa Rica", "🇨🇷"),
                d("Andalusian Spanish (Seville)", "Southern Spain", "🇪🇸"),
                d("Canarian Spanish", "Canary Islands", "🇪🇸"),
                d("Murcian Spanish", "Southeastern Spain", "🇪🇸"),
                d("Catalan-accented Spanish (Barcelona)", "Catalonia", "🇪🇸"),
                d("Galician-accented Spanish", "Galicia", "🇪🇸"),
                d("Equatoguinean Spanish", "Equatorial Guinea", "🇬🇶"),
                d("US Spanish (Los Angeles)", "United States", "🇺🇸"),
            ),
        ),
        Language(
            "English", "English", "🇬🇧", Region.EUROPE, speakersMillions = 1500,
            dialects = listOf(
                d("General American English", "United States", "🇺🇸"),
                d("British English (Received Pronunciation)", "England", "🇬🇧"),
                d("London English (Estuary)", "London & South East England", "🇬🇧"),
                d("Cockney English", "East London", "🇬🇧"),
                d("Northern English (Manchester)", "North West England", "🇬🇧"),
                d("Yorkshire English", "Yorkshire", "🇬🇧"),
                d("Geordie English (Newcastle)", "North East England", "🇬🇧"),
                d("Scouse English (Liverpool)", "Merseyside", "🇬🇧"),
                d("Brummie English (Birmingham)", "West Midlands", "🇬🇧"),
                d("West Country English", "South West England", "🇬🇧"),
                d("Scottish English (Edinburgh)", "Scotland", "🏴󠁧󠁢󠁳󠁣󠁴󠁿"),
                d("Glaswegian English", "Glasgow", "🏴󠁧󠁢󠁳󠁣󠁴󠁿"),
                d("Welsh English (Cardiff)", "Wales", "🏴󠁧󠁢󠁷󠁬󠁳󠁿"),
                d("Northern Irish English (Belfast)", "Northern Ireland", "🇬🇧"),
                d("Irish English (Dublin)", "Ireland", "🇮🇪"),
                d("Cork English", "Southern Ireland", "🇮🇪"),
                d("Southern American English (Texas)", "Southern United States", "🇺🇸"),
                d("New York English", "New York City", "🇺🇸"),
                d("Boston English", "New England", "🇺🇸"),
                d("Midwestern English (Chicago)", "Great Lakes", "🇺🇸"),
                d("Californian English", "West Coast United States", "🇺🇸"),
                d("African American English", "United States", "🇺🇸"),
                d("Canadian English (Toronto)", "Canada", "🇨🇦"),
                d("Australian English (Sydney)", "Australia", "🇦🇺"),
                d("Broad Australian English", "Rural Australia", "🇦🇺"),
                d("New Zealand English (Auckland)", "New Zealand", "🇳🇿"),
                d("South African English (Johannesburg)", "South Africa", "🇿🇦"),
                d("Indian English (Mumbai)", "India", "🇮🇳"),
                d("Singaporean English", "Singapore", "🇸🇬"),
                d("Hong Kong English", "Hong Kong", "🇭🇰"),
                d("Filipino English", "Philippines", "🇵🇭"),
                d("Nigerian English (Lagos)", "Nigeria", "🇳🇬"),
                d("Kenyan English (Nairobi)", "Kenya", "🇰🇪"),
                d("Ghanaian English", "Ghana", "🇬🇭"),
                d("Jamaican English", "Jamaica", "🇯🇲"),
                d("Caribbean English (Trinidad)", "Trinidad & Tobago", "🇹🇹"),
            ),
        ),
        Language(
            "French", "Français", "🇫🇷", Region.EUROPE, speakersMillions = 310,
            dialects = listOf(
                d("Parisian French", "Paris · Île-de-France", "🇫🇷"),
                d("Southern French (Marseille)", "Provence · Occitania", "🇫🇷"),
                d("Lyonnais French", "Lyon", "🇫🇷"),
                d("Northern French (Ch'ti, Lille)", "Hauts-de-France", "🇫🇷"),
                d("Alsatian French (Strasbourg)", "Alsace", "🇫🇷"),
                d("Breton French", "Brittany", "🇫🇷"),
                d("Corsican French", "Corsica", "🇫🇷"),
                d("Québec French (Montréal)", "Québec, Canada", "🇨🇦"),
                d("Acadian French", "New Brunswick · Nova Scotia", "🇨🇦"),
                d("Ontario French", "Ontario, Canada", "🇨🇦"),
                d("Belgian French (Brussels)", "Belgium", "🇧🇪"),
                d("Swiss French (Geneva)", "Romandy, Switzerland", "🇨🇭"),
                d("Luxembourgish French", "Luxembourg", "🇱🇺"),
                d("Ivorian French (Abidjan)", "Côte d'Ivoire", "🇨🇮"),
                d("Senegalese French (Dakar)", "Senegal", "🇸🇳"),
                d("Cameroonian French", "Cameroon", "🇨🇲"),
                d("Congolese French (Kinshasa)", "DR Congo", "🇨🇩"),
                d("Malian French", "Mali", "🇲🇱"),
                d("Moroccan French", "Morocco", "🇲🇦"),
                d("Algerian French", "Algeria", "🇩🇿"),
                d("Tunisian French", "Tunisia", "🇹🇳"),
                d("Lebanese French", "Lebanon", "🇱🇧"),
                d("Haitian French", "Haiti", "🇭🇹"),
                d("Antillean French (Martinique)", "Martinique · Guadeloupe", "🇫🇷"),
                d("Réunion French", "Réunion", "🇫🇷"),
                d("Louisiana French (Cajun)", "Louisiana, USA", "🇺🇸"),
                d("Tahitian French", "French Polynesia", "🇵🇫"),
            ),
        ),
        Language(
            "Portuguese", "Português", "🇵🇹", Region.EUROPE, speakersMillions = 260,
            dialects = listOf(
                d("Brazilian Portuguese (São Paulo)", "São Paulo, Brazil", "🇧🇷"),
                d("Carioca Portuguese (Rio de Janeiro)", "Rio de Janeiro, Brazil", "🇧🇷"),
                d("Mineiro Portuguese (Belo Horizonte)", "Minas Gerais, Brazil", "🇧🇷"),
                d("Nordestino Portuguese (Recife)", "Northeast Brazil", "🇧🇷"),
                d("Baiano Portuguese (Salvador)", "Bahia, Brazil", "🇧🇷"),
                d("Gaúcho Portuguese (Porto Alegre)", "Rio Grande do Sul, Brazil", "🇧🇷"),
                d("Brasília Portuguese", "Federal District, Brazil", "🇧🇷"),
                d("Amazonian Portuguese (Manaus)", "Northern Brazil", "🇧🇷"),
                d("European Portuguese (Lisbon)", "Lisbon, Portugal", "🇵🇹"),
                d("Northern Portuguese (Porto)", "Porto, Portugal", "🇵🇹"),
                d("Alentejo Portuguese", "Southern Portugal", "🇵🇹"),
                d("Azorean Portuguese", "Azores", "🇵🇹"),
                d("Madeiran Portuguese", "Madeira", "🇵🇹"),
                d("Angolan Portuguese (Luanda)", "Angola", "🇦🇴"),
                d("Mozambican Portuguese (Maputo)", "Mozambique", "🇲🇿"),
                d("Cape Verdean Portuguese", "Cape Verde", "🇨🇻"),
                d("Guinea-Bissau Portuguese", "Guinea-Bissau", "🇬🇼"),
                d("São Toméan Portuguese", "São Tomé and Príncipe", "🇸🇹"),
                d("Timorese Portuguese", "East Timor", "🇹🇱"),
                d("Macanese Portuguese", "Macau", "🇲🇴"),
            ),
        ),
        Language(
            "German", "Deutsch", "🇩🇪", Region.EUROPE, speakersMillions = 135,
            dialects = listOf(
                d("Standard German (Hochdeutsch)", "Germany", "🇩🇪"),
                d("Berlin German", "Berlin", "🇩🇪"),
                d("Bavarian German (Munich)", "Bavaria", "🇩🇪"),
                d("Swabian German (Stuttgart)", "Baden-Württemberg", "🇩🇪"),
                d("Franconian German (Nuremberg)", "Northern Bavaria", "🇩🇪"),
                d("Saxon German (Dresden, Leipzig)", "Saxony", "🇩🇪"),
                d("Hamburg German", "Northern Germany", "🇩🇪"),
                d("Low German (Plattdeutsch)", "Northern Germany", "🇩🇪"),
                d("Rhineland German (Cologne, Kölsch)", "North Rhine-Westphalia", "🇩🇪"),
                d("Hessian German (Frankfurt)", "Hesse", "🇩🇪"),
                d("Palatinate German (Pfälzisch)", "Rhineland-Palatinate", "🇩🇪"),
                d("Austrian German (Vienna)", "Austria", "🇦🇹"),
                d("Tyrolean German (Innsbruck)", "Tyrol, Austria", "🇦🇹"),
                d("Styrian German (Graz)", "Styria, Austria", "🇦🇹"),
                d("Swiss German (Zürich)", "Switzerland", "🇨🇭"),
                d("Bernese German", "Bern, Switzerland", "🇨🇭"),
                d("Basel German", "Basel, Switzerland", "🇨🇭"),
                d("Swiss Standard German", "Switzerland (formal)", "🇨🇭"),
                d("Luxembourgish German", "Luxembourg", "🇱🇺"),
                d("South Tyrolean German", "South Tyrol, Italy", "🇮🇹"),
                d("Liechtenstein German", "Liechtenstein", "🇱🇮"),
                d("Namibian German", "Namibia", "🇳🇦"),
            ),
        ),
        Language(
            "Italian", "Italiano", "🇮🇹", Region.EUROPE, speakersMillions = 68,
            dialects = listOf(
                d("Standard Italian", "Italy", "🇮🇹"),
                d("Roman Italian", "Rome · Lazio", "🇮🇹"),
                d("Milanese Italian", "Milan · Lombardy", "🇮🇹"),
                d("Florentine Italian", "Florence · Tuscany", "🇮🇹"),
                d("Neapolitan Italian", "Naples · Campania", "🇮🇹"),
                d("Venetian Italian", "Venice · Veneto", "🇮🇹"),
                d("Turinese Italian", "Turin · Piedmont", "🇮🇹"),
                d("Genoese Italian", "Genoa · Liguria", "🇮🇹"),
                d("Bolognese Italian", "Bologna · Emilia-Romagna", "🇮🇹"),
                d("Sicilian Italian (Palermo)", "Sicily", "🇮🇹"),
                d("Sardinian Italian (Cagliari)", "Sardinia", "🇮🇹"),
                d("Apulian Italian (Bari)", "Puglia", "🇮🇹"),
                d("Calabrian Italian", "Calabria", "🇮🇹"),
                d("Abruzzese Italian", "Abruzzo", "🇮🇹"),
                d("Trentino Italian", "Trentino-Alto Adige", "🇮🇹"),
                d("Swiss Italian (Lugano)", "Ticino, Switzerland", "🇨🇭"),
                d("Sammarinese Italian", "San Marino", "🇸🇲"),
            ),
        ),
        Language(
            "Dutch", "Nederlands", "🇳🇱", Region.EUROPE, speakersMillions = 25,
            dialects = listOf(
                d("Standard Dutch (Randstad)", "Netherlands", "🇳🇱"),
                d("Amsterdam Dutch", "Amsterdam", "🇳🇱"),
                d("Rotterdam Dutch", "Rotterdam", "🇳🇱"),
                d("The Hague Dutch", "The Hague", "🇳🇱"),
                d("Brabantian Dutch (Eindhoven)", "North Brabant", "🇳🇱"),
                d("Limburgish Dutch (Maastricht)", "Limburg", "🇳🇱"),
                d("Groningen Dutch", "Northern Netherlands", "🇳🇱"),
                d("Frisian-accented Dutch", "Friesland", "🇳🇱"),
                d("Flemish Dutch (Antwerp)", "Flanders, Belgium", "🇧🇪"),
                d("Brussels Flemish", "Brussels", "🇧🇪"),
                d("West Flemish (Bruges)", "West Flanders", "🇧🇪"),
                d("Ghent Flemish", "East Flanders", "🇧🇪"),
                d("Surinamese Dutch (Paramaribo)", "Suriname", "🇸🇷"),
                d("Caribbean Dutch (Curaçao)", "Aruba · Curaçao", "🇨🇼"),
            ),
        ),
        Language(
            "Russian", "Русский", "🇷🇺", Region.EUROPE, speakersMillions = 255,
            dialects = listOf(
                d("Standard Russian (Moscow)", "Moscow", "🇷🇺"),
                d("St. Petersburg Russian", "St. Petersburg", "🇷🇺"),
                d("Southern Russian (Rostov, Krasnodar)", "Southern Russia", "🇷🇺"),
                d("Northern Russian (Arkhangelsk)", "Northern Russia", "🇷🇺"),
                d("Volga Russian (Kazan, Samara)", "Volga region", "🇷🇺"),
                d("Ural Russian (Yekaterinburg)", "Urals", "🇷🇺"),
                d("Siberian Russian (Novosibirsk)", "Siberia", "🇷🇺"),
                d("Far Eastern Russian (Vladivostok)", "Russian Far East", "🇷🇺"),
                d("Ukrainian-accented Russian (Odesa)", "Ukraine", "🇺🇦"),
                d("Belarusian Russian (Minsk)", "Belarus", "🇧🇾"),
                d("Kazakhstani Russian (Almaty)", "Kazakhstan", "🇰🇿"),
                d("Baltic Russian (Riga)", "Latvia · Estonia · Lithuania", "🇱🇻"),
                d("Moldovan Russian", "Moldova", "🇲🇩"),
                d("Israeli Russian", "Israel", "🇮🇱"),
            ),
        ),
        Language(
            "Ukrainian", "Українська", "🇺🇦", Region.EUROPE, speakersMillions = 40,
            dialects = listOf(
                d("Standard Ukrainian (Kyiv)", "Kyiv", "🇺🇦"),
                d("Western Ukrainian (Lviv)", "Galicia", "🇺🇦"),
                d("Transcarpathian Ukrainian", "Zakarpattia", "🇺🇦"),
                d("Central Ukrainian (Poltava)", "Poltava region", "🇺🇦"),
                d("Southern Ukrainian (Odesa)", "Odesa region", "🇺🇦"),
                d("Eastern Ukrainian (Kharkiv)", "Kharkiv region", "🇺🇦"),
                d("Surzhyk-influenced Ukrainian", "Mixed Ukrainian–Russian", "🇺🇦"),
            ),
        ),
        Language(
            "Polish", "Polski", "🇵🇱", Region.EUROPE, speakersMillions = 45,
            dialects = listOf(
                d("Standard Polish (Warsaw)", "Warsaw", "🇵🇱"),
                d("Kraków Polish (Lesser Poland)", "Kraków", "🇵🇱"),
                d("Silesian Polish (Katowice)", "Silesia", "🇵🇱"),
                d("Greater Poland Polish (Poznań)", "Poznań", "🇵🇱"),
                d("Pomeranian Polish (Gdańsk)", "Pomerania", "🇵🇱"),
                d("Kashubian-accented Polish", "Pomerania", "🇵🇱"),
                d("Podhale Polish (Highlander)", "Tatra mountains", "🇵🇱"),
                d("Eastern Polish (Lublin, Białystok)", "Eastern Poland", "🇵🇱"),
                d("Łódź Polish", "Central Poland", "🇵🇱"),
            ),
        ),
        Language(
            "Czech", "Čeština", "🇨🇿", Region.EUROPE, speakersMillions = 11,
            dialects = listOf(
                d("Standard Czech (Prague)", "Bohemia", "🇨🇿"),
                d("Common Czech (Obecná čeština)", "Everyday Bohemian speech", "🇨🇿"),
                d("Moravian Czech (Brno)", "Moravia", "🇨🇿"),
                d("Silesian Czech (Ostrava)", "Czech Silesia", "🇨🇿"),
                d("Hanakian Czech (Olomouc)", "Central Moravia", "🇨🇿"),
            ),
        ),
        Language(
            "Slovak", "Slovenčina", "🇸🇰", Region.EUROPE, speakersMillions = 5,
            dialects = listOf(
                d("Standard Slovak (Bratislava)", "Western Slovakia", "🇸🇰"),
                d("Central Slovak (Banská Bystrica)", "Central Slovakia", "🇸🇰"),
                d("Eastern Slovak (Košice)", "Eastern Slovakia", "🇸🇰"),
            ),
        ),
        Language(
            "Hungarian", "Magyar", "🇭🇺", Region.EUROPE, speakersMillions = 13,
            dialects = listOf(
                d("Standard Hungarian (Budapest)", "Budapest", "🇭🇺"),
                d("Transdanubian Hungarian (Pécs)", "Western Hungary", "🇭🇺"),
                d("Great Plain Hungarian (Szeged, Debrecen)", "Eastern Hungary", "🇭🇺"),
                d("Palóc Hungarian", "Northern Hungary", "🇭🇺"),
                d("Transylvanian Hungarian (Cluj)", "Romania", "🇷🇴"),
                d("Székely Hungarian", "Eastern Transylvania", "🇷🇴"),
            ),
        ),
        Language(
            "Romanian", "Română", "🇷🇴", Region.EUROPE, speakersMillions = 25,
            dialects = listOf(
                d("Standard Romanian (Bucharest)", "Wallachia", "🇷🇴"),
                d("Moldavian Romanian (Iași)", "Moldavia region", "🇷🇴"),
                d("Transylvanian Romanian (Cluj)", "Transylvania", "🇷🇴"),
                d("Banat Romanian (Timișoara)", "Banat", "🇷🇴"),
                d("Oltenian Romanian (Craiova)", "Oltenia", "🇷🇴"),
                d("Moldovan Romanian (Chișinău)", "Moldova", "🇲🇩"),
            ),
        ),
        Language(
            "Greek", "Ελληνικά", "🇬🇷", Region.EUROPE, speakersMillions = 13,
            dialects = listOf(
                d("Standard Greek (Athens)", "Athens", "🇬🇷"),
                d("Northern Greek (Thessaloniki)", "Macedonia, Greece", "🇬🇷"),
                d("Cretan Greek", "Crete", "🇬🇷"),
                d("Peloponnesian Greek", "Peloponnese", "🇬🇷"),
                d("Ionian Greek (Corfu)", "Ionian Islands", "🇬🇷"),
                d("Dodecanese Greek (Rhodes)", "Dodecanese", "🇬🇷"),
                d("Cypriot Greek (Nicosia)", "Cyprus", "🇨🇾"),
                d("Pontic Greek", "Pontic diaspora", "🇬🇷"),
            ),
        ),
        Language(
            "Turkish", "Türkçe", "🇹🇷", Region.EUROPE, speakersMillions = 90,
            dialects = listOf(
                d("Istanbul Turkish", "Istanbul (standard)", "🇹🇷"),
                d("Ankara Turkish", "Central Anatolia", "🇹🇷"),
                d("Aegean Turkish (İzmir)", "Aegean region", "🇹🇷"),
                d("Black Sea Turkish (Trabzon)", "Karadeniz", "🇹🇷"),
                d("Eastern Anatolian Turkish (Erzurum)", "Eastern Turkey", "🇹🇷"),
                d("Southeastern Turkish (Gaziantep)", "Southeastern Turkey", "🇹🇷"),
                d("Mediterranean Turkish (Antalya)", "Southern Turkey", "🇹🇷"),
                d("Cypriot Turkish", "Northern Cyprus", "🇨🇾"),
                d("Balkan Turkish (Rumelian)", "Bulgaria · North Macedonia", "🇧🇬"),
                d("German-Turkish (Berlin)", "Turkish diaspora in Germany", "🇩🇪"),
            ),
        ),
        Language(
            "Swedish", "Svenska", "🇸🇪", Region.EUROPE, speakersMillions = 10,
            dialects = listOf(
                d("Standard Swedish (Stockholm)", "Stockholm", "🇸🇪"),
                d("Gothenburg Swedish", "Västra Götaland", "🇸🇪"),
                d("Scanian Swedish (Malmö)", "Skåne", "🇸🇪"),
                d("Norrland Swedish (Umeå)", "Northern Sweden", "🇸🇪"),
                d("Dalecarlian Swedish", "Dalarna", "🇸🇪"),
                d("Gotland Swedish", "Gotland", "🇸🇪"),
                d("Finland Swedish (Helsinki)", "Finland", "🇫🇮"),
                d("Rinkeby Swedish", "Multiethnic urban Swedish", "🇸🇪"),
            ),
        ),
        Language(
            "Norwegian", "Norsk", "🇳🇴", Region.EUROPE, speakersMillions = 5,
            dialects = listOf(
                d("Eastern Norwegian (Oslo)", "Oslo · Østlandet", "🇳🇴"),
                d("Bergen Norwegian", "Bergen", "🇳🇴"),
                d("Trøndersk Norwegian (Trondheim)", "Trøndelag", "🇳🇴"),
                d("Stavanger Norwegian", "Rogaland", "🇳🇴"),
                d("Northern Norwegian (Tromsø)", "Nord-Norge", "🇳🇴"),
                d("Sognemål Norwegian", "Vestland", "🇳🇴"),
                d("Nynorsk-based Norwegian", "Western Norway (written Nynorsk)", "🇳🇴"),
            ),
        ),
        Language(
            "Danish", "Dansk", "🇩🇰", Region.EUROPE, speakersMillions = 6,
            dialects = listOf(
                d("Standard Danish (Copenhagen)", "Copenhagen", "🇩🇰"),
                d("Jutlandic Danish (Aarhus)", "Jutland", "🇩🇰"),
                d("Southern Jutlandic Danish (Sønderjysk)", "Southern Jutland", "🇩🇰"),
                d("Funen Danish (Odense)", "Funen", "🇩🇰"),
                d("Bornholm Danish", "Bornholm", "🇩🇰"),
            ),
        ),
        Language(
            "Finnish", "Suomi", "🇫🇮", Region.EUROPE, speakersMillions = 5,
            dialects = listOf(
                d("Standard Finnish (Helsinki)", "Helsinki", "🇫🇮"),
                d("Helsinki slang (Stadin slangi)", "Helsinki", "🇫🇮"),
                d("Tampere Finnish", "Pirkanmaa", "🇫🇮"),
                d("Turku Finnish", "Southwest Finland", "🇫🇮"),
                d("Savo Finnish (Kuopio)", "Eastern Finland", "🇫🇮"),
                d("Oulu Finnish", "Northern Ostrobothnia", "🇫🇮"),
                d("Lapland Finnish (Rovaniemi)", "Lapland", "🇫🇮"),
                d("Karelian Finnish", "Eastern Finland", "🇫🇮"),
            ),
        ),
        Language(
            "Icelandic", "Íslenska", "🇮🇸", Region.EUROPE, speakersMillions = 1,
            dialects = listOf(
                d("Standard Icelandic (Reykjavík)", "Reykjavík", "🇮🇸"),
                d("Northern Icelandic (Akureyri)", "Northern Iceland", "🇮🇸"),
            ),
        ),
        Language(
            "Irish", "Gaeilge", "🇮🇪", Region.EUROPE, speakersMillions = 2,
            dialects = listOf(
                d("Connacht Irish (Connemara)", "Galway", "🇮🇪"),
                d("Munster Irish (Kerry)", "Cork · Kerry", "🇮🇪"),
                d("Ulster Irish (Donegal)", "Donegal", "🇮🇪"),
                d("Standard Irish (An Caighdeán)", "Official standard", "🇮🇪"),
            ),
        ),
        Language(
            "Scottish Gaelic", "Gàidhlig", "🏴󠁧󠁢󠁳󠁣󠁴󠁿", Region.EUROPE, speakersMillions = 1,
            dialects = listOf(
                d("Lewis Gaelic", "Isle of Lewis", "🏴󠁧󠁢󠁳󠁣󠁴󠁿"),
                d("Skye Gaelic", "Isle of Skye", "🏴󠁧󠁢󠁳󠁣󠁴󠁿"),
                d("Argyll Gaelic", "Argyll", "🏴󠁧󠁢󠁳󠁣󠁴󠁿"),
            ),
        ),
        Language(
            "Welsh", "Cymraeg", "🏴󠁧󠁢󠁷󠁬󠁳󠁿", Region.EUROPE, speakersMillions = 1,
            dialects = listOf(
                d("Northern Welsh (Gwynedd)", "North Wales", "🏴󠁧󠁢󠁷󠁬󠁳󠁿"),
                d("Southern Welsh (Cardiff, Swansea)", "South Wales", "🏴󠁧󠁢󠁷󠁬󠁳󠁿"),
                d("Mid Wales Welsh (Ceredigion)", "West Wales", "🏴󠁧󠁢󠁷󠁬󠁳󠁿"),
                d("Patagonian Welsh", "Chubut, Argentina", "🇦🇷"),
            ),
        ),
        Language(
            "Catalan", "Català", "🇪🇸", Region.EUROPE, speakersMillions = 10,
            dialects = listOf(
                d("Central Catalan (Barcelona)", "Barcelona", "🇪🇸"),
                d("Valencian", "Valencia", "🇪🇸"),
                d("Balearic Catalan (Mallorca)", "Balearic Islands", "🇪🇸"),
                d("North-Western Catalan (Lleida)", "Lleida", "🇪🇸"),
                d("Northern Catalan (Perpignan)", "Roussillon, France", "🇫🇷"),
                d("Alguerese Catalan", "Alghero, Sardinia", "🇮🇹"),
                d("Andorran Catalan", "Andorra", "🇦🇩"),
            ),
        ),
        Language(
            "Basque", "Euskara", "🇪🇸", Region.EUROPE, speakersMillions = 1,
            dialects = listOf(
                d("Standard Basque (Batua)", "Unified standard", "🇪🇸"),
                d("Gipuzkoan Basque (San Sebastián)", "Gipuzkoa", "🇪🇸"),
                d("Biscayan Basque (Bilbao)", "Biscay", "🇪🇸"),
                d("Navarrese Basque", "Navarre", "🇪🇸"),
                d("Souletin Basque", "French Basque Country", "🇫🇷"),
            ),
        ),
        Language(
            "Galician", "Galego", "🇪🇸", Region.EUROPE, speakersMillions = 2,
            dialects = listOf(
                d("Standard Galician (Santiago)", "Santiago de Compostela", "🇪🇸"),
                d("Western Galician (A Coruña, Vigo)", "Coastal Galicia", "🇪🇸"),
                d("Eastern Galician (Lugo, Ourense)", "Inland Galicia", "🇪🇸"),
            ),
        ),
        Language(
            "Croatian", "Hrvatski", "🇭🇷", Region.EUROPE, speakersMillions = 6,
            dialects = listOf(
                d("Standard Croatian (Zagreb)", "Zagreb", "🇭🇷"),
                d("Dalmatian Croatian (Split)", "Dalmatia", "🇭🇷"),
                d("Istrian Croatian (Pula)", "Istria", "🇭🇷"),
                d("Slavonian Croatian (Osijek)", "Slavonia", "🇭🇷"),
                d("Dubrovnik Croatian", "Southern Dalmatia", "🇭🇷"),
                d("Kajkavian Croatian (Zagorje)", "Northern Croatia", "🇭🇷"),
            ),
        ),
        Language(
            "Serbian", "Српски", "🇷🇸", Region.EUROPE, speakersMillions = 9,
            dialects = listOf(
                d("Standard Serbian (Belgrade)", "Belgrade", "🇷🇸"),
                d("Vojvodina Serbian (Novi Sad)", "Vojvodina", "🇷🇸"),
                d("Southern Serbian (Niš)", "Southern Serbia", "🇷🇸"),
                d("Bosnian Serbian (Banja Luka)", "Republika Srpska", "🇧🇦"),
                d("Montenegrin (Podgorica)", "Montenegro", "🇲🇪"),
            ),
        ),
        Language(
            "Bosnian", "Bosanski", "🇧🇦", Region.EUROPE, speakersMillions = 3,
            dialects = listOf(
                d("Standard Bosnian (Sarajevo)", "Sarajevo", "🇧🇦"),
                d("Herzegovinian Bosnian (Mostar)", "Herzegovina", "🇧🇦"),
                d("Tuzla Bosnian", "Northeastern Bosnia", "🇧🇦"),
            ),
        ),
        Language(
            "Slovene", "Slovenščina", "🇸🇮", Region.EUROPE, speakersMillions = 2,
            dialects = listOf(
                d("Standard Slovene (Ljubljana)", "Ljubljana", "🇸🇮"),
                d("Styrian Slovene (Maribor)", "Štajerska", "🇸🇮"),
                d("Littoral Slovene (Koper)", "Primorska", "🇸🇮"),
                d("Carinthian Slovene", "Koroška · Austria", "🇸🇮"),
                d("Prekmurje Slovene", "Northeastern Slovenia", "🇸🇮"),
            ),
        ),
        Language(
            "Bulgarian", "Български", "🇧🇬", Region.EUROPE, speakersMillions = 8,
            dialects = listOf(
                d("Standard Bulgarian (Sofia)", "Sofia", "🇧🇬"),
                d("Eastern Bulgarian (Varna)", "Black Sea coast", "🇧🇬"),
                d("Plovdiv Bulgarian (Rhodope)", "Southern Bulgaria", "🇧🇬"),
                d("Northern Bulgarian (Pleven)", "Danubian plain", "🇧🇬"),
                d("Shopski Bulgarian", "Western Bulgaria", "🇧🇬"),
            ),
        ),
        Language(
            "Macedonian", "Македонски", "🇲🇰", Region.EUROPE, speakersMillions = 2,
            dialects = listOf(
                d("Standard Macedonian (Skopje)", "Skopje", "🇲🇰"),
                d("Western Macedonian (Bitola, Ohrid)", "Southwest", "🇲🇰"),
                d("Eastern Macedonian (Štip)", "East", "🇲🇰"),
            ),
        ),
        Language(
            "Albanian", "Shqip", "🇦🇱", Region.EUROPE, speakersMillions = 8,
            dialects = listOf(
                d("Standard Albanian (Tirana)", "Tirana", "🇦🇱"),
                d("Tosk Albanian (Southern)", "Southern Albania", "🇦🇱"),
                d("Gheg Albanian (Shkodër)", "Northern Albania", "🇦🇱"),
                d("Kosovar Albanian (Pristina)", "Kosovo", "🇽🇰"),
                d("Macedonian Albanian (Tetovo)", "North Macedonia", "🇲🇰"),
                d("Arbëresh", "Southern Italy", "🇮🇹"),
            ),
        ),
        Language(
            "Lithuanian", "Lietuvių", "🇱🇹", Region.EUROPE, speakersMillions = 3,
            dialects = listOf(
                d("Standard Lithuanian (Vilnius)", "Vilnius", "🇱🇹"),
                d("Aukštaitian Lithuanian (Kaunas)", "Highlands", "🇱🇹"),
                d("Samogitian (Klaipėda)", "Lowlands", "🇱🇹"),
            ),
        ),
        Language(
            "Latvian", "Latviešu", "🇱🇻", Region.EUROPE, speakersMillions = 2,
            dialects = listOf(
                d("Standard Latvian (Riga)", "Riga", "🇱🇻"),
                d("Latgalian (Daugavpils)", "Latgale", "🇱🇻"),
                d("Livonian-accented Latvian (Kurzeme)", "Courland", "🇱🇻"),
            ),
        ),
        Language(
            "Estonian", "Eesti", "🇪🇪", Region.EUROPE, speakersMillions = 1,
            dialects = listOf(
                d("Standard Estonian (Tallinn)", "Tallinn", "🇪🇪"),
                d("Tartu Estonian", "Southern Estonia", "🇪🇪"),
                d("Võro (South Estonian)", "Võru County", "🇪🇪"),
                d("Island Estonian (Saaremaa)", "West Estonian islands", "🇪🇪"),
            ),
        ),
        Language(
            "Belarusian", "Беларуская", "🇧🇾", Region.EUROPE, speakersMillions = 5,
            dialects = listOf(
                d("Standard Belarusian (Minsk)", "Minsk", "🇧🇾"),
                d("Northeastern Belarusian (Vitebsk)", "Vitebsk region", "🇧🇾"),
                d("Southwestern Belarusian (Brest)", "Brest region", "🇧🇾"),
                d("Trasianka-influenced Belarusian", "Mixed Belarusian–Russian", "🇧🇾"),
            ),
        ),
        Language(
            "Maltese", "Malti", "🇲🇹", Region.EUROPE, speakersMillions = 1,
            dialects = listOf(
                d("Standard Maltese (Valletta)", "Malta", "🇲🇹"),
                d("Gozitan Maltese", "Gozo", "🇲🇹"),
            ),
        ),
        Language(
            "Luxembourgish", "Lëtzebuergesch", "🇱🇺", Region.EUROPE, speakersMillions = 1,
            dialects = listOf(
                d("Standard Luxembourgish (Luxembourg City)", "Luxembourg", "🇱🇺"),
                d("Northern Luxembourgish (Éislek)", "Ardennes", "🇱🇺"),
            ),
        ),
        Language(
            "Yiddish", "ייִדיש", "🇮🇱", Region.EUROPE, speakersMillions = 1,
            dialects = listOf(
                d("Standard (YIVO) Yiddish", "Literary standard", "🇮🇱"),
                d("Hasidic Yiddish (Brooklyn)", "New York", "🇺🇸"),
                d("Litvish Yiddish", "Northeastern (Lithuanian)", "🇱🇹"),
                d("Poylish Yiddish", "Central (Polish)", "🇵🇱"),
                d("Hungarian (Unterlander) Yiddish", "Southeastern", "🇭🇺"),
            ),
        ),
        Language(
            "Esperanto", "Esperanto", "🏳️", Region.EUROPE, speakersMillions = 1,
            dialects = listOf(
                d("Standard Esperanto", "International", "🏳️"),
            ),
        ),

        // ───────────── East & Southeast Asia ─────────────
        Language(
            "Mandarin Chinese", "普通话 / 國語", "🇨🇳", Region.ASIA, speakersMillions = 1100,
            dialects = listOf(
                d("Standard Mandarin (Beijing, Putonghua)", "Beijing", "🇨🇳"),
                d("Taiwanese Mandarin (Taipei, Guoyu)", "Taiwan", "🇹🇼"),
                d("Northeastern Mandarin (Harbin, Dongbei)", "Northeast China", "🇨🇳"),
                d("Tianjin Mandarin", "Tianjin", "🇨🇳"),
                d("Shandong Mandarin (Jinan)", "Shandong", "🇨🇳"),
                d("Shanghai-accented Mandarin", "Shanghai", "🇨🇳"),
                d("Nanjing Mandarin", "Jiangsu", "🇨🇳"),
                d("Sichuanese Mandarin (Chengdu)", "Sichuan", "🇨🇳"),
                d("Chongqing Mandarin", "Chongqing", "🇨🇳"),
                d("Wuhan Mandarin", "Hubei", "🇨🇳"),
                d("Xi'an Mandarin (Guanzhong)", "Shaanxi", "🇨🇳"),
                d("Yunnan Mandarin (Kunming)", "Yunnan", "🇨🇳"),
                d("Guangdong-accented Mandarin (Guangzhou)", "Guangdong", "🇨🇳"),
                d("Fujian-accented Mandarin (Xiamen)", "Fujian", "🇨🇳"),
                d("Singaporean Mandarin (Huayu)", "Singapore", "🇸🇬"),
                d("Malaysian Mandarin (Kuala Lumpur)", "Malaysia", "🇲🇾"),
                d("Hong Kong Mandarin", "Hong Kong", "🇭🇰"),
                d("Overseas Chinese Mandarin (San Francisco)", "Diaspora", "🇺🇸"),
            ),
        ),
        Language(
            "Cantonese", "廣東話", "🇭🇰", Region.ASIA, speakersMillions = 85,
            dialects = listOf(
                d("Hong Kong Cantonese", "Hong Kong", "🇭🇰"),
                d("Guangzhou Cantonese", "Guangdong", "🇨🇳"),
                d("Macau Cantonese", "Macau", "🇲🇴"),
                d("Taishanese (Toisan)", "Jiangmen · overseas Chinatowns", "🇨🇳"),
                d("Malaysian Cantonese (Kuala Lumpur, Ipoh)", "Malaysia", "🇲🇾"),
                d("Vancouver / Toronto Cantonese", "Canada", "🇨🇦"),
            ),
        ),
        Language(
            "Hokkien / Taiwanese", "台語 / 閩南語", "🇹🇼", Region.ASIA, speakersMillions = 45,
            dialects = listOf(
                d("Taiwanese Hokkien (Tainan)", "Taiwan", "🇹🇼"),
                d("Xiamen (Amoy) Hokkien", "Fujian", "🇨🇳"),
                d("Quanzhou Hokkien", "Fujian", "🇨🇳"),
                d("Penang Hokkien", "Malaysia", "🇲🇾"),
                d("Singaporean Hokkien", "Singapore", "🇸🇬"),
                d("Medan Hokkien", "Indonesia", "🇮🇩"),
                d("Philippine Hokkien (Lannang)", "Philippines", "🇵🇭"),
            ),
        ),
        Language(
            "Shanghainese", "上海话", "🇨🇳", Region.ASIA, speakersMillions = 14,
            dialects = listOf(
                d("Urban Shanghainese", "Shanghai", "🇨🇳"),
                d("Suzhou Wu", "Jiangsu", "🇨🇳"),
                d("Hangzhou Wu", "Zhejiang", "🇨🇳"),
            ),
        ),
        Language(
            "Hakka", "客家話", "🇨🇳", Region.ASIA, speakersMillions = 45,
            dialects = listOf(
                d("Meixian Hakka", "Guangdong", "🇨🇳"),
                d("Taiwanese Hakka (Sixian)", "Taiwan", "🇹🇼"),
                d("Taiwanese Hakka (Hailu)", "Taiwan", "🇹🇼"),
                d("Malaysian Hakka", "Malaysia", "🇲🇾"),
            ),
        ),
        Language(
            "Japanese", "日本語", "🇯🇵", Region.ASIA, speakersMillions = 125,
            dialects = listOf(
                d("Standard Japanese (Tokyo)", "Tokyo", "🇯🇵"),
                d("Kansai Japanese (Osaka)", "Osaka", "🇯🇵"),
                d("Kyoto Japanese", "Kyoto", "🇯🇵"),
                d("Kobe Japanese", "Hyōgo", "🇯🇵"),
                d("Nagoya Japanese", "Aichi", "🇯🇵"),
                d("Hiroshima Japanese", "Chūgoku", "🇯🇵"),
                d("Hakata Japanese (Fukuoka)", "Kyushu", "🇯🇵"),
                d("Kagoshima Japanese (Satsuma)", "Southern Kyushu", "🇯🇵"),
                d("Kumamoto Japanese", "Kyushu", "🇯🇵"),
                d("Tōhoku Japanese (Sendai)", "Northeast Japan", "🇯🇵"),
                d("Tsugaru Japanese (Aomori)", "Northern Tōhoku", "🇯🇵"),
                d("Hokkaido Japanese (Sapporo)", "Hokkaido", "🇯🇵"),
                d("Okinawan Japanese (Uchinaa-Yamatuguchi)", "Okinawa", "🇯🇵"),
                d("Hokuriku Japanese (Kanazawa)", "Ishikawa", "🇯🇵"),
                d("Shizuoka Japanese", "Chūbu", "🇯🇵"),
                d("Sanuki Japanese (Kagawa)", "Shikoku", "🇯🇵"),
                d("Tosa Japanese (Kōchi)", "Shikoku", "🇯🇵"),
            ),
        ),
        Language(
            "Korean", "한국어", "🇰🇷", Region.ASIA, speakersMillions = 80,
            dialects = listOf(
                d("Standard Korean (Seoul)", "Seoul · Gyeonggi", "🇰🇷"),
                d("Gyeongsang Korean (Busan)", "Busan · Southeast", "🇰🇷"),
                d("Daegu Korean", "North Gyeongsang", "🇰🇷"),
                d("Jeolla Korean (Gwangju)", "Southwest", "🇰🇷"),
                d("Chungcheong Korean (Daejeon)", "Central", "🇰🇷"),
                d("Gangwon Korean", "Northeast", "🇰🇷"),
                d("Jeju Korean", "Jeju Island", "🇰🇷"),
                d("Pyongyang Korean (Munhwaŏ)", "North Korea", "🇰🇵"),
                d("Hamgyŏng Korean", "Northeast North Korea", "🇰🇵"),
                d("Korean-Chinese (Yanbian)", "Jilin, China", "🇨🇳"),
                d("Koryo-mar", "Central Asia", "🇰🇿"),
                d("Korean American (Los Angeles)", "Diaspora", "🇺🇸"),
            ),
        ),
        Language(
            "Vietnamese", "Tiếng Việt", "🇻🇳", Region.ASIA, speakersMillions = 85,
            dialects = listOf(
                d("Northern Vietnamese (Hanoi)", "Hanoi", "🇻🇳"),
                d("Southern Vietnamese (Saigon / Ho Chi Minh City)", "Ho Chi Minh City", "🇻🇳"),
                d("Central Vietnamese (Huế)", "Huế", "🇻🇳"),
                d("Đà Nẵng Vietnamese", "Central coast", "🇻🇳"),
                d("Nghệ An Vietnamese", "North Central", "🇻🇳"),
                d("Hải Phòng Vietnamese", "Northern coast", "🇻🇳"),
                d("Mekong Delta Vietnamese (Cần Thơ)", "Southwest", "🇻🇳"),
                d("Overseas Vietnamese (Orange County)", "Diaspora", "🇺🇸"),
            ),
        ),
        Language(
            "Thai", "ไทย", "🇹🇭", Region.ASIA, speakersMillions = 60,
            dialects = listOf(
                d("Central Thai (Bangkok)", "Bangkok", "🇹🇭"),
                d("Northern Thai (Chiang Mai, Kam Mueang)", "Lanna", "🇹🇭"),
                d("Isan Thai (Khon Kaen)", "Northeast", "🇹🇭"),
                d("Southern Thai (Phuket, Nakhon Si Thammarat)", "South", "🇹🇭"),
                d("Khorat Thai", "Nakhon Ratchasima", "🇹🇭"),
            ),
        ),
        Language(
            "Lao", "ລາວ", "🇱🇦", Region.ASIA, speakersMillions = 7,
            dialects = listOf(
                d("Vientiane Lao", "Vientiane", "🇱🇦"),
                d("Luang Prabang Lao", "Northern Laos", "🇱🇦"),
                d("Southern Lao (Pakse)", "Champasak", "🇱🇦"),
            ),
        ),
        Language(
            "Khmer", "ខ្មែរ", "🇰🇭", Region.ASIA, speakersMillions = 17,
            dialects = listOf(
                d("Standard Khmer (Phnom Penh)", "Phnom Penh", "🇰🇭"),
                d("Battambang Khmer", "Northwest", "🇰🇭"),
                d("Khmer Krom", "Mekong Delta, Vietnam", "🇻🇳"),
                d("Northern Khmer (Surin)", "Thailand", "🇹🇭"),
            ),
        ),
        Language(
            "Burmese", "မြန်မာ", "🇲🇲", Region.ASIA, speakersMillions = 35,
            dialects = listOf(
                d("Standard Burmese (Yangon)", "Yangon", "🇲🇲"),
                d("Mandalay Burmese", "Upper Myanmar", "🇲🇲"),
                d("Rakhine (Arakanese)", "Rakhine State", "🇲🇲"),
                d("Tavoyan Burmese (Dawei)", "Tanintharyi", "🇲🇲"),
            ),
        ),
        Language(
            "Indonesian", "Bahasa Indonesia", "🇮🇩", Region.ASIA, speakersMillions = 200,
            dialects = listOf(
                d("Standard Indonesian (Jakarta)", "Jakarta", "🇮🇩"),
                d("Jakartan colloquial (Bahasa Gaul)", "Jakarta everyday speech", "🇮🇩"),
                d("Javanese-accented Indonesian (Surabaya, Yogyakarta)", "Java", "🇮🇩"),
                d("Sundanese-accented Indonesian (Bandung)", "West Java", "🇮🇩"),
                d("Balinese-accented Indonesian (Denpasar)", "Bali", "🇮🇩"),
                d("Medan Indonesian", "North Sumatra", "🇮🇩"),
                d("Padang Indonesian (Minang)", "West Sumatra", "🇮🇩"),
                d("Makassar Indonesian", "South Sulawesi", "🇮🇩"),
                d("Manado Indonesian", "North Sulawesi", "🇮🇩"),
                d("Papuan Indonesian (Jayapura)", "Papua", "🇮🇩"),
                d("Ambonese Indonesian", "Maluku", "🇮🇩"),
            ),
        ),
        Language(
            "Malay", "Bahasa Melayu", "🇲🇾", Region.ASIA, speakersMillions = 80,
            dialects = listOf(
                d("Standard Malay (Kuala Lumpur)", "Malaysia", "🇲🇾"),
                d("Kelantanese Malay", "Kelantan", "🇲🇾"),
                d("Terengganu Malay", "Terengganu", "🇲🇾"),
                d("Kedah Malay (Penang)", "Northern Malaysia", "🇲🇾"),
                d("Johor-Riau Malay", "Southern Malaysia", "🇲🇾"),
                d("Sarawak Malay (Kuching)", "Borneo", "🇲🇾"),
                d("Sabah Malay (Kota Kinabalu)", "Borneo", "🇲🇾"),
                d("Singaporean Malay", "Singapore", "🇸🇬"),
                d("Bruneian Malay", "Brunei", "🇧🇳"),
            ),
        ),
        Language(
            "Javanese", "Basa Jawa", "🇮🇩", Region.ASIA, speakersMillions = 80,
            dialects = listOf(
                d("Central Javanese (Yogyakarta, Solo)", "Central Java", "🇮🇩"),
                d("East Javanese (Surabaya, Arekan)", "East Java", "🇮🇩"),
                d("Banyumasan Javanese (Ngapak)", "Western Central Java", "🇮🇩"),
                d("Cirebon Javanese", "West Java", "🇮🇩"),
                d("Suriname Javanese", "Suriname", "🇸🇷"),
            ),
        ),
        Language(
            "Filipino / Tagalog", "Filipino", "🇵🇭", Region.ASIA, speakersMillions = 85,
            dialects = listOf(
                d("Manila Tagalog (Filipino)", "Metro Manila", "🇵🇭"),
                d("Batangas Tagalog", "Batangas", "🇵🇭"),
                d("Bulacan Tagalog", "Bulacan", "🇵🇭"),
                d("Taglish (Manila)", "Code-switched Tagalog–English", "🇵🇭"),
                d("Marinduque Tagalog", "Marinduque", "🇵🇭"),
                d("Overseas Filipino (California)", "Diaspora", "🇺🇸"),
            ),
        ),
        Language(
            "Cebuano", "Bisaya", "🇵🇭", Region.ASIA, speakersMillions = 25,
            dialects = listOf(
                d("Cebu City Cebuano", "Cebu", "🇵🇭"),
                d("Davao Cebuano (Davaoeño)", "Mindanao", "🇵🇭"),
                d("Cagayan de Oro Cebuano", "Northern Mindanao", "🇵🇭"),
                d("Boholano", "Bohol", "🇵🇭"),
                d("Leyte Cebuano", "Leyte", "🇵🇭"),
            ),
        ),
        Language(
            "Ilocano", "Ilokano", "🇵🇭", Region.ASIA, speakersMillions = 10,
            dialects = listOf(
                d("Northern Ilocano (Vigan, Laoag)", "Ilocos", "🇵🇭"),
                d("Southern Ilocano (La Union, Pangasinan)", "Northern Luzon", "🇵🇭"),
                d("Hawaiian Ilocano", "Hawaii", "🇺🇸"),
            ),
        ),
        Language(
            "Mongolian", "Монгол", "🇲🇳", Region.ASIA, speakersMillions = 6,
            dialects = listOf(
                d("Khalkha Mongolian (Ulaanbaatar)", "Mongolia", "🇲🇳"),
                d("Inner Mongolian (Chakhar, Hohhot)", "Inner Mongolia, China", "🇨🇳"),
                d("Buryat", "Buryatia, Russia", "🇷🇺"),
                d("Oirat / Kalmyk", "Western Mongolia · Kalmykia", "🇲🇳"),
            ),
        ),
        Language(
            "Tibetan", "བོད་སྐད", "🏔️", Region.ASIA, speakersMillions = 6,
            dialects = listOf(
                d("Lhasa Tibetan (Ü-Tsang)", "Central Tibet", "🏔️"),
                d("Amdo Tibetan", "Qinghai · Gansu", "🏔️"),
                d("Kham Tibetan", "Eastern Tibet · Sichuan", "🏔️"),
                d("Ladakhi", "Ladakh, India", "🇮🇳"),
                d("Exile Tibetan (Dharamsala)", "India", "🇮🇳"),
            ),
        ),

        // ───────────── South Asia ─────────────
        Language(
            "Hindi", "हिन्दी", "🇮🇳", Region.SOUTH_ASIA, speakersMillions = 600,
            dialects = listOf(
                d("Standard Hindi (Delhi, Khariboli)", "Delhi", "🇮🇳"),
                d("Mumbai Hindi (Bambaiya)", "Mumbai", "🇮🇳"),
                d("Lucknow Hindi (Awadhi-influenced)", "Uttar Pradesh", "🇮🇳"),
                d("Bihari Hindi (Patna)", "Bihar", "🇮🇳"),
                d("Bhojpuri-accented Hindi (Varanasi)", "Eastern UP · Bihar", "🇮🇳"),
                d("Haryanvi Hindi (Chandigarh)", "Haryana", "🇮🇳"),
                d("Rajasthani Hindi (Jaipur)", "Rajasthan", "🇮🇳"),
                d("Madhya Pradesh Hindi (Bhopal, Indore)", "Central India", "🇮🇳"),
                d("Chhattisgarhi Hindi", "Chhattisgarh", "🇮🇳"),
                d("Pahari Hindi (Shimla, Dehradun)", "Himalayan foothills", "🇮🇳"),
                d("Hinglish (Urban India)", "Code-switched Hindi–English", "🇮🇳"),
                d("Fiji Hindi", "Fiji", "🇫🇯"),
                d("Caribbean Hindustani (Trinidad, Suriname)", "Caribbean", "🇹🇹"),
                d("British Asian Hindi (London)", "Diaspora", "🇬🇧"),
            ),
        ),
        Language(
            "Urdu", "اردو", "🇵🇰", Region.SOUTH_ASIA, speakersMillions = 230,
            dialects = listOf(
                d("Standard Urdu (Karachi)", "Karachi", "🇵🇰"),
                d("Lahori Urdu (Punjabi-influenced)", "Lahore", "🇵🇰"),
                d("Islamabad / Rawalpindi Urdu", "Northern Punjab", "🇵🇰"),
                d("Lucknow Urdu (Lakhnavi)", "Uttar Pradesh, India", "🇮🇳"),
                d("Delhi Urdu (Dehlavi)", "Delhi, India", "🇮🇳"),
                d("Hyderabadi Urdu (Dakhni)", "Hyderabad, India", "🇮🇳"),
                d("Peshawari Urdu (Pashto-influenced)", "Khyber Pakhtunkhwa", "🇵🇰"),
                d("British Pakistani Urdu (Birmingham)", "Diaspora", "🇬🇧"),
            ),
        ),
        Language(
            "Bengali", "বাংলা", "🇧🇩", Region.SOUTH_ASIA, speakersMillions = 270,
            dialects = listOf(
                d("Standard Bengali (Dhaka)", "Bangladesh", "🇧🇩"),
                d("Kolkata Bengali (Rarhi)", "West Bengal, India", "🇮🇳"),
                d("Chittagonian", "Chittagong", "🇧🇩"),
                d("Sylheti", "Sylhet · British Bangladeshi", "🇧🇩"),
                d("Rangpuri / Rajbanshi", "Northern Bangladesh", "🇧🇩"),
                d("Barisal Bengali", "Southern Bangladesh", "🇧🇩"),
                d("Noakhali Bengali", "Southeastern Bangladesh", "🇧🇩"),
                d("Sylheti-accented Bengali (London)", "Diaspora", "🇬🇧"),
            ),
        ),
        Language(
            "Punjabi", "ਪੰਜਾਬੀ / پنجابی", "🇮🇳", Region.SOUTH_ASIA, speakersMillions = 125,
            dialects = listOf(
                d("Majhi Punjabi (Lahore, Amritsar)", "Central Punjab (standard)", "🇵🇰"),
                d("Doabi Punjabi (Jalandhar)", "Doaba, India", "🇮🇳"),
                d("Malwai Punjabi (Ludhiana, Patiala)", "Malwa, India", "🇮🇳"),
                d("Pothohari Punjabi (Rawalpindi)", "Northern Pakistan", "🇵🇰"),
                d("Multani / Saraiki", "Southern Punjab, Pakistan", "🇵🇰"),
                d("Hindko", "Khyber Pakhtunkhwa", "🇵🇰"),
                d("Canadian Punjabi (Surrey, Brampton)", "Diaspora", "🇨🇦"),
                d("British Punjabi (Southall)", "Diaspora", "🇬🇧"),
            ),
        ),
        Language(
            "Tamil", "தமிழ்", "🇮🇳", Region.SOUTH_ASIA, speakersMillions = 85,
            dialects = listOf(
                d("Chennai Tamil (Madras Bashai)", "Chennai", "🇮🇳"),
                d("Central Tamil (Tiruchirappalli, Thanjavur)", "Tamil Nadu standard", "🇮🇳"),
                d("Kongu Tamil (Coimbatore)", "Western Tamil Nadu", "🇮🇳"),
                d("Madurai Tamil", "Southern Tamil Nadu", "🇮🇳"),
                d("Tirunelveli / Nellai Tamil", "Far south", "🇮🇳"),
                d("Sri Lankan Tamil (Jaffna)", "Northern Sri Lanka", "🇱🇰"),
                d("Batticaloa Tamil", "Eastern Sri Lanka", "🇱🇰"),
                d("Singaporean Tamil", "Singapore", "🇸🇬"),
                d("Malaysian Tamil (Kuala Lumpur)", "Malaysia", "🇲🇾"),
            ),
        ),
        Language(
            "Telugu", "తెలుగు", "🇮🇳", Region.SOUTH_ASIA, speakersMillions = 95,
            dialects = listOf(
                d("Coastal Andhra Telugu (Vijayawada, Guntur)", "Andhra Pradesh (standard)", "🇮🇳"),
                d("Telangana Telugu (Hyderabad)", "Telangana", "🇮🇳"),
                d("Rayalaseema Telugu (Tirupati, Kurnool)", "Southern Andhra", "🇮🇳"),
                d("Uttarandhra Telugu (Visakhapatnam)", "Northern coast", "🇮🇳"),
                d("Godavari Telugu (Rajahmundry)", "East & West Godavari", "🇮🇳"),
            ),
        ),
        Language(
            "Marathi", "मराठी", "🇮🇳", Region.SOUTH_ASIA, speakersMillions = 95,
            dialects = listOf(
                d("Standard Marathi (Pune)", "Pune", "🇮🇳"),
                d("Mumbai Marathi", "Mumbai", "🇮🇳"),
                d("Varhadi Marathi (Nagpur)", "Vidarbha", "🇮🇳"),
                d("Kolhapuri Marathi", "Southern Maharashtra", "🇮🇳"),
                d("Konkani-influenced Marathi (Ratnagiri)", "Konkan coast", "🇮🇳"),
                d("Marathwada Marathi (Aurangabad)", "Marathwada", "🇮🇳"),
                d("Ahirani / Khandeshi", "Northern Maharashtra", "🇮🇳"),
            ),
        ),
        Language(
            "Gujarati", "ગુજરાતી", "🇮🇳", Region.SOUTH_ASIA, speakersMillions = 60,
            dialects = listOf(
                d("Standard Gujarati (Ahmedabad)", "Central Gujarat", "🇮🇳"),
                d("Surti Gujarati (Surat)", "South Gujarat", "🇮🇳"),
                d("Kathiawadi Gujarati (Rajkot)", "Saurashtra", "🇮🇳"),
                d("Kachchhi", "Kutch", "🇮🇳"),
                d("Charotari Gujarati (Anand)", "Central Gujarat", "🇮🇳"),
                d("East African Gujarati (Nairobi)", "Diaspora", "🇰🇪"),
                d("British Gujarati (Leicester)", "Diaspora", "🇬🇧"),
            ),
        ),
        Language(
            "Kannada", "ಕನ್ನಡ", "🇮🇳", Region.SOUTH_ASIA, speakersMillions = 60,
            dialects = listOf(
                d("Bengaluru Kannada", "Bengaluru", "🇮🇳"),
                d("Mysuru Kannada", "Old Mysore (standard)", "🇮🇳"),
                d("Dharwad Kannada (Hubballi)", "Northern Karnataka", "🇮🇳"),
                d("Mangaluru Kannada", "Coastal Karnataka", "🇮🇳"),
                d("Kundapura Kannada", "Coastal Karnataka", "🇮🇳"),
                d("Gulbarga Kannada", "Kalyana-Karnataka", "🇮🇳"),
            ),
        ),
        Language(
            "Malayalam", "മലയാളം", "🇮🇳", Region.SOUTH_ASIA, speakersMillions = 38,
            dialects = listOf(
                d("Central Malayalam (Kochi, Thrissur)", "Central Kerala", "🇮🇳"),
                d("Thiruvananthapuram Malayalam", "Southern Kerala", "🇮🇳"),
                d("Kozhikode Malayalam (Malabar)", "Northern Kerala", "🇮🇳"),
                d("Kottayam Malayalam", "Central Travancore", "🇮🇳"),
                d("Kannur / Kasaragod Malayalam", "Far north Kerala", "🇮🇳"),
                d("Gulf Malayalam (Dubai)", "Diaspora", "🇦🇪"),
            ),
        ),
        Language(
            "Odia", "ଓଡ଼ିଆ", "🇮🇳", Region.SOUTH_ASIA, speakersMillions = 38,
            dialects = listOf(
                d("Standard Odia (Bhubaneswar, Cuttack)", "Coastal Odisha", "🇮🇳"),
                d("Sambalpuri Odia", "Western Odisha", "🇮🇳"),
                d("Southern Odia (Berhampur)", "Ganjam", "🇮🇳"),
                d("Northern Odia (Balasore)", "Northern Odisha", "🇮🇳"),
            ),
        ),
        Language(
            "Sinhala", "සිංහල", "🇱🇰", Region.SOUTH_ASIA, speakersMillions = 17,
            dialects = listOf(
                d("Standard Sinhala (Colombo)", "Western Province", "🇱🇰"),
                d("Kandyan Sinhala (Up-country)", "Central Province", "🇱🇰"),
                d("Southern Sinhala (Galle, Matara)", "Southern Province", "🇱🇰"),
                d("Sabaragamuwa Sinhala", "Ratnapura", "🇱🇰"),
            ),
        ),
        Language(
            "Nepali", "नेपाली", "🇳🇵", Region.SOUTH_ASIA, speakersMillions = 30,
            dialects = listOf(
                d("Standard Nepali (Kathmandu)", "Kathmandu", "🇳🇵"),
                d("Eastern Nepali (Dharan)", "Eastern Nepal", "🇳🇵"),
                d("Western Nepali (Pokhara)", "Gandaki", "🇳🇵"),
                d("Far-Western Nepali (Doteli)", "Sudurpashchim", "🇳🇵"),
                d("Darjeeling Nepali", "West Bengal, India", "🇮🇳"),
                d("Sikkimese Nepali", "Sikkim, India", "🇮🇳"),
            ),
        ),
        Language(
            "Assamese", "অসমীয়া", "🇮🇳", Region.SOUTH_ASIA, speakersMillions = 15,
            dialects = listOf(
                d("Standard Assamese (Guwahati)", "Central Assam", "🇮🇳"),
                d("Eastern Assamese (Dibrugarh)", "Upper Assam", "🇮🇳"),
                d("Kamrupi Assamese", "Western Assam", "🇮🇳"),
                d("Goalpariya Assamese", "Western Assam", "🇮🇳"),
            ),
        ),
        Language(
            "Sindhi", "سنڌي", "🇵🇰", Region.SOUTH_ASIA, speakersMillions = 30,
            dialects = listOf(
                d("Vicholi Sindhi (Hyderabad, Sindh)", "Central Sindh (standard)", "🇵🇰"),
                d("Lari Sindhi (Karachi, Thatta)", "Lower Sindh", "🇵🇰"),
                d("Siroli Sindhi (Sukkur)", "Upper Sindh", "🇵🇰"),
                d("Thari Sindhi", "Tharparkar", "🇵🇰"),
                d("Indian Sindhi (Ulhasnagar)", "India", "🇮🇳"),
            ),
        ),
        Language(
            "Pashto", "پښتو", "🇦🇫", Region.SOUTH_ASIA, speakersMillions = 50,
            dialects = listOf(
                d("Kandahari Pashto (Southern)", "Kandahar", "🇦🇫"),
                d("Kabuli Pashto", "Kabul", "🇦🇫"),
                d("Peshawari Pashto (Yusufzai)", "Peshawar", "🇵🇰"),
                d("Quetta Pashto", "Balochistan", "🇵🇰"),
                d("Waziri Pashto", "Waziristan", "🇵🇰"),
            ),
        ),
        Language(
            "Dhivehi", "ދިވެހި", "🇲🇻", Region.SOUTH_ASIA, speakersMillions = 1,
            dialects = listOf(
                d("Malé Dhivehi", "Maldives", "🇲🇻"),
                d("Southern Dhivehi (Addu)", "Southern atolls", "🇲🇻"),
            ),
        ),

        // ───────────── Middle East & Central Asia ─────────────
        Language(
            "Arabic", "العربية", "🇸🇦", Region.MIDDLE_EAST, speakersMillions = 420,
            dialects = listOf(
                d("Modern Standard Arabic (Fusha)", "Formal · pan-Arab", "🇸🇦"),
                d("Egyptian Arabic (Cairo)", "Egypt", "🇪🇬"),
                d("Alexandrian Arabic", "Egypt", "🇪🇬"),
                d("Sa'idi Arabic (Upper Egypt)", "Egypt", "🇪🇬"),
                d("Levantine Arabic (Beirut)", "Lebanon", "🇱🇧"),
                d("Syrian Arabic (Damascus)", "Syria", "🇸🇾"),
                d("Aleppo Arabic", "Syria", "🇸🇾"),
                d("Jordanian Arabic (Amman)", "Jordan", "🇯🇴"),
                d("Palestinian Arabic (Jerusalem)", "Palestine", "🇵🇸"),
                d("Gaza Arabic", "Palestine", "🇵🇸"),
                d("Iraqi Arabic (Baghdad)", "Iraq", "🇮🇶"),
                d("Basrawi Arabic", "Southern Iraq", "🇮🇶"),
                d("Moslawi Arabic", "Northern Iraq", "🇮🇶"),
                d("Gulf Arabic (Dubai)", "UAE", "🇦🇪"),
                d("Kuwaiti Arabic", "Kuwait", "🇰🇼"),
                d("Bahraini Arabic", "Bahrain", "🇧🇭"),
                d("Qatari Arabic", "Qatar", "🇶🇦"),
                d("Saudi Najdi Arabic (Riyadh)", "Central Saudi Arabia", "🇸🇦"),
                d("Saudi Hejazi Arabic (Jeddah)", "Western Saudi Arabia", "🇸🇦"),
                d("Omani Arabic (Muscat)", "Oman", "🇴🇲"),
                d("Yemeni Arabic (Sana'a)", "Yemen", "🇾🇪"),
                d("Hadhrami Arabic", "Southern Yemen", "🇾🇪"),
                d("Sudanese Arabic (Khartoum)", "Sudan", "🇸🇩"),
                d("Chadian Arabic", "Chad", "🇹🇩"),
                d("Libyan Arabic (Tripoli)", "Libya", "🇱🇾"),
                d("Tunisian Arabic (Tunis)", "Tunisia", "🇹🇳"),
                d("Algerian Arabic (Algiers)", "Algeria", "🇩🇿"),
                d("Moroccan Arabic (Casablanca, Darija)", "Morocco", "🇲🇦"),
                d("Fessi Arabic (Fez)", "Morocco", "🇲🇦"),
                d("Hassaniya Arabic", "Mauritania · Western Sahara", "🇲🇷"),
                d("Maltese-influenced Arabic", "Malta", "🇲🇹"),
                d("Cypriot Maronite Arabic", "Cyprus", "🇨🇾"),
            ),
        ),
        Language(
            "Hebrew", "עברית", "🇮🇱", Region.MIDDLE_EAST, speakersMillions = 9,
            dialects = listOf(
                d("Standard Israeli Hebrew (Tel Aviv)", "Israel", "🇮🇱"),
                d("Jerusalem Hebrew", "Jerusalem", "🇮🇱"),
                d("Mizrahi Hebrew", "Middle Eastern Jewish pronunciation", "🇮🇱"),
                d("Ashkenazi-influenced Hebrew", "European Jewish pronunciation", "🇮🇱"),
                d("Yemenite Hebrew", "Yemenite Jewish tradition", "🇮🇱"),
                d("American-accented Hebrew", "Diaspora", "🇺🇸"),
            ),
        ),
        Language(
            "Persian", "فارسی", "🇮🇷", Region.MIDDLE_EAST, speakersMillions = 110,
            dialects = listOf(
                d("Tehrani Persian", "Tehran", "🇮🇷"),
                d("Isfahani Persian", "Isfahan", "🇮🇷"),
                d("Shirazi Persian", "Shiraz", "🇮🇷"),
                d("Mashhadi Persian", "Khorasan", "🇮🇷"),
                d("Yazdi Persian", "Yazd", "🇮🇷"),
                d("Dari (Kabul)", "Afghanistan", "🇦🇫"),
                d("Herati Dari", "Western Afghanistan", "🇦🇫"),
                d("Hazaragi", "Central Afghanistan", "🇦🇫"),
                d("Tajik (Dushanbe)", "Tajikistan", "🇹🇯"),
                d("Bukharan Tajik (Samarkand, Bukhara)", "Uzbekistan", "🇺🇿"),
                d("Tehrangeles Persian (Los Angeles)", "Diaspora", "🇺🇸"),
            ),
        ),
        Language(
            "Kurdish", "Kurdî / کوردی", "🟢", Region.MIDDLE_EAST, speakersMillions = 30,
            dialects = listOf(
                d("Kurmanji Kurdish (Diyarbakır)", "Turkey · Syria", "🇹🇷"),
                d("Sorani Kurdish (Erbil, Sulaymaniyah)", "Iraqi Kurdistan", "🇮🇶"),
                d("Kermanshahi Kurdish", "Iran", "🇮🇷"),
                d("Zazaki", "Eastern Turkey", "🇹🇷"),
                d("Afrin Kurmanji", "Syria", "🇸🇾"),
            ),
        ),
        Language(
            "Azerbaijani", "Azərbaycan dili", "🇦🇿", Region.MIDDLE_EAST, speakersMillions = 30,
            dialects = listOf(
                d("Baku Azerbaijani", "Azerbaijan", "🇦🇿"),
                d("Ganja Azerbaijani", "Western Azerbaijan", "🇦🇿"),
                d("Nakhchivan Azerbaijani", "Nakhchivan", "🇦🇿"),
                d("Iranian Azerbaijani (Tabriz)", "Iran", "🇮🇷"),
                d("Ardabil Azerbaijani", "Iran", "🇮🇷"),
            ),
        ),
        Language(
            "Armenian", "Հայերեն", "🇦🇲", Region.MIDDLE_EAST, speakersMillions = 7,
            dialects = listOf(
                d("Eastern Armenian (Yerevan)", "Armenia", "🇦🇲"),
                d("Western Armenian (Beirut)", "Lebanese diaspora", "🇱🇧"),
                d("Western Armenian (Istanbul)", "Turkey", "🇹🇷"),
                d("Karabakh Armenian", "Artsakh", "🇦🇲"),
                d("Los Angeles Armenian (Glendale)", "Diaspora", "🇺🇸"),
                d("Iranian Armenian (Tehran, Isfahan)", "Iran", "🇮🇷"),
            ),
        ),
        Language(
            "Georgian", "ქართული", "🇬🇪", Region.MIDDLE_EAST, speakersMillions = 4,
            dialects = listOf(
                d("Standard Georgian (Tbilisi)", "Tbilisi", "🇬🇪"),
                d("Kakhetian Georgian", "Eastern Georgia", "🇬🇪"),
                d("Imeretian Georgian (Kutaisi)", "Western Georgia", "🇬🇪"),
                d("Gurian Georgian", "Southwest Georgia", "🇬🇪"),
                d("Adjarian Georgian (Batumi)", "Adjara", "🇬🇪"),
            ),
        ),
        Language(
            "Kazakh", "Қазақша", "🇰🇿", Region.MIDDLE_EAST, speakersMillions = 14,
            dialects = listOf(
                d("Standard Kazakh (Astana, Almaty)", "Kazakhstan", "🇰🇿"),
                d("Southern Kazakh (Shymkent)", "Southern Kazakhstan", "🇰🇿"),
                d("Western Kazakh (Atyrau)", "Western Kazakhstan", "🇰🇿"),
                d("Chinese Kazakh (Xinjiang)", "China", "🇨🇳"),
            ),
        ),
        Language(
            "Uzbek", "Oʻzbekcha", "🇺🇿", Region.MIDDLE_EAST, speakersMillions = 35,
            dialects = listOf(
                d("Tashkent Uzbek", "Tashkent", "🇺🇿"),
                d("Fergana Uzbek", "Fergana Valley", "🇺🇿"),
                d("Samarkand / Bukhara Uzbek", "Central Uzbekistan", "🇺🇿"),
                d("Khorezm Uzbek", "Western Uzbekistan", "🇺🇿"),
                d("Afghan Uzbek (Mazar-i-Sharif)", "Afghanistan", "🇦🇫"),
            ),
        ),
        Language(
            "Kyrgyz", "Кыргызча", "🇰🇬", Region.MIDDLE_EAST, speakersMillions = 5,
            dialects = listOf(
                d("Northern Kyrgyz (Bishkek)", "Northern Kyrgyzstan", "🇰🇬"),
                d("Southern Kyrgyz (Osh)", "Southern Kyrgyzstan", "🇰🇬"),
            ),
        ),
        Language(
            "Turkmen", "Türkmençe", "🇹🇲", Region.MIDDLE_EAST, speakersMillions = 7,
            dialects = listOf(
                d("Standard Turkmen (Ashgabat, Teke)", "Turkmenistan", "🇹🇲"),
                d("Yomut Turkmen", "Western Turkmenistan", "🇹🇲"),
                d("Iranian Turkmen (Gonbad-e Kavus)", "Iran", "🇮🇷"),
            ),
        ),
        Language(
            "Uyghur", "ئۇيغۇرچە", "🇨🇳", Region.MIDDLE_EAST, speakersMillions = 11,
            dialects = listOf(
                d("Central Uyghur (Ürümqi, Turpan)", "Xinjiang (standard)", "🇨🇳"),
                d("Southern Uyghur (Kashgar, Hotan)", "Southern Xinjiang", "🇨🇳"),
                d("Kazakhstani Uyghur (Almaty)", "Kazakhstan", "🇰🇿"),
            ),
        ),

        // ───────────── Africa ─────────────
        Language(
            "Swahili", "Kiswahili", "🇹🇿", Region.AFRICA, speakersMillions = 100,
            dialects = listOf(
                d("Standard Swahili (Dar es Salaam, Kiunguja)", "Tanzania", "🇹🇿"),
                d("Zanzibari Swahili", "Zanzibar", "🇹🇿"),
                d("Kenyan Swahili (Nairobi)", "Kenya", "🇰🇪"),
                d("Sheng (Nairobi)", "Urban Swahili–English slang", "🇰🇪"),
                d("Mombasa Swahili (Kimvita)", "Kenyan coast", "🇰🇪"),
                d("Congolese Swahili (Lubumbashi, Kingwana)", "DR Congo", "🇨🇩"),
                d("Ugandan Swahili (Kampala)", "Uganda", "🇺🇬"),
                d("Comorian Swahili", "Comoros", "🇰🇲"),
            ),
        ),
        Language(
            "Amharic", "አማርኛ", "🇪🇹", Region.AFRICA, speakersMillions = 57,
            dialects = listOf(
                d("Addis Ababa Amharic", "Central Ethiopia (standard)", "🇪🇹"),
                d("Gondar Amharic", "Northern Ethiopia", "🇪🇹"),
                d("Gojjam Amharic", "Northwestern Ethiopia", "🇪🇹"),
                d("Wollo Amharic", "Northeastern Ethiopia", "🇪🇹"),
                d("Menz Amharic", "Northern Shewa", "🇪🇹"),
            ),
        ),
        Language(
            "Tigrinya", "ትግርኛ", "🇪🇷", Region.AFRICA, speakersMillions = 9,
            dialects = listOf(
                d("Asmara Tigrinya", "Eritrea", "🇪🇷"),
                d("Mekelle Tigrinya", "Tigray, Ethiopia", "🇪🇹"),
            ),
        ),
        Language(
            "Oromo", "Afaan Oromoo", "🇪🇹", Region.AFRICA, speakersMillions = 37,
            dialects = listOf(
                d("Central Oromo (Shewa)", "Central Ethiopia", "🇪🇹"),
                d("Western Oromo (Wellega)", "Western Ethiopia", "🇪🇹"),
                d("Eastern Oromo (Harar)", "Eastern Ethiopia", "🇪🇹"),
                d("Southern Oromo (Borana)", "Southern Ethiopia · Kenya", "🇰🇪"),
            ),
        ),
        Language(
            "Somali", "Soomaali", "🇸🇴", Region.AFRICA, speakersMillions = 22,
            dialects = listOf(
                d("Northern Somali (Mogadishu standard)", "Somalia", "🇸🇴"),
                d("Somaliland Somali (Hargeisa)", "Somaliland", "🇸🇴"),
                d("Benadiri Somali", "Mogadishu coast", "🇸🇴"),
                d("Maay Somali (Baidoa)", "Southern Somalia", "🇸🇴"),
                d("Djiboutian Somali", "Djibouti", "🇩🇯"),
                d("Ethiopian Somali (Jigjiga)", "Ethiopia", "🇪🇹"),
                d("Minneapolis Somali", "Diaspora", "🇺🇸"),
            ),
        ),
        Language(
            "Hausa", "Hausa", "🇳🇬", Region.AFRICA, speakersMillions = 80,
            dialects = listOf(
                d("Kano Hausa (Kananci)", "Northern Nigeria (standard)", "🇳🇬"),
                d("Sokoto Hausa (Sakkwatanci)", "Northwestern Nigeria", "🇳🇬"),
                d("Zaria Hausa (Zazzaganci)", "Kaduna", "🇳🇬"),
                d("Katsina Hausa", "Katsina", "🇳🇬"),
                d("Nigerien Hausa (Maradi, Zinder)", "Niger", "🇳🇪"),
                d("Ghanaian Hausa (Accra)", "Ghana", "🇬🇭"),
            ),
        ),
        Language(
            "Yoruba", "Yorùbá", "🇳🇬", Region.AFRICA, speakersMillions = 45,
            dialects = listOf(
                d("Standard Yoruba (Lagos, Ibadan)", "Southwestern Nigeria", "🇳🇬"),
                d("Ọyọ Yoruba", "Oyo", "🇳🇬"),
                d("Ekiti Yoruba", "Ekiti", "🇳🇬"),
                d("Ijebu Yoruba", "Ogun", "🇳🇬"),
                d("Egba Yoruba (Abeokuta)", "Ogun", "🇳🇬"),
                d("Ondo Yoruba", "Ondo", "🇳🇬"),
                d("Beninese Yoruba (Nago)", "Benin", "🇧🇯"),
            ),
        ),
        Language(
            "Igbo", "Igbo", "🇳🇬", Region.AFRICA, speakersMillions = 30,
            dialects = listOf(
                d("Standard Igbo (Owerri, Umuahia)", "Southeastern Nigeria", "🇳🇬"),
                d("Onitsha Igbo", "Anambra", "🇳🇬"),
                d("Enugu Igbo (Nsukka)", "Enugu", "🇳🇬"),
                d("Abia Igbo (Aba)", "Abia", "🇳🇬"),
                d("Ika Igbo (Delta)", "Delta State", "🇳🇬"),
            ),
        ),
        Language(
            "Nigerian Pidgin", "Naijá", "🇳🇬", Region.AFRICA, speakersMillions = 75,
            dialects = listOf(
                d("Lagos Pidgin", "Lagos", "🇳🇬"),
                d("Warri Pidgin", "Delta State", "🇳🇬"),
                d("Port Harcourt Pidgin", "Rivers State", "🇳🇬"),
                d("Cameroonian Pidgin (Kamtok)", "Cameroon", "🇨🇲"),
                d("Ghanaian Pidgin", "Ghana", "🇬🇭"),
            ),
        ),
        Language(
            "Zulu", "isiZulu", "🇿🇦", Region.AFRICA, speakersMillions = 28,
            dialects = listOf(
                d("Standard Zulu (Durban, KwaZulu-Natal)", "KwaZulu-Natal", "🇿🇦"),
                d("Johannesburg Zulu (urban)", "Gauteng", "🇿🇦"),
                d("Northern Zulu (Zululand)", "Northern KZN", "🇿🇦"),
            ),
        ),
        Language(
            "Xhosa", "isiXhosa", "🇿🇦", Region.AFRICA, speakersMillions = 20,
            dialects = listOf(
                d("Standard Xhosa (Eastern Cape)", "Eastern Cape", "🇿🇦"),
                d("Cape Town Xhosa", "Western Cape", "🇿🇦"),
                d("Mpondo Xhosa", "Pondoland", "🇿🇦"),
                d("Thembu Xhosa", "Eastern Cape interior", "🇿🇦"),
            ),
        ),
        Language(
            "Afrikaans", "Afrikaans", "🇿🇦", Region.AFRICA, speakersMillions = 17,
            dialects = listOf(
                d("Standard Afrikaans (Pretoria)", "Gauteng", "🇿🇦"),
                d("Cape Afrikaans (Kaaps, Cape Town)", "Western Cape", "🇿🇦"),
                d("Oranjerivier Afrikaans", "Northern Cape", "🇿🇦"),
                d("Eastern Border Afrikaans", "Eastern Cape · Free State", "🇿🇦"),
                d("Namibian Afrikaans (Windhoek)", "Namibia", "🇳🇦"),
            ),
        ),
        Language(
            "Sesotho", "Sesotho", "🇱🇸", Region.AFRICA, speakersMillions = 6,
            dialects = listOf(
                d("Lesotho Sesotho (Maseru)", "Lesotho", "🇱🇸"),
                d("Free State Sesotho", "South Africa", "🇿🇦"),
                d("Johannesburg Sesotho", "Gauteng", "🇿🇦"),
            ),
        ),
        Language(
            "Setswana", "Setswana", "🇧🇼", Region.AFRICA, speakersMillions = 8,
            dialects = listOf(
                d("Botswana Setswana (Gaborone)", "Botswana", "🇧🇼"),
                d("South African Setswana (Mahikeng)", "North West Province", "🇿🇦"),
            ),
        ),
        Language(
            "Shona", "chiShona", "🇿🇼", Region.AFRICA, speakersMillions = 15,
            dialects = listOf(
                d("Zezuru Shona (Harare)", "Central Zimbabwe (standard)", "🇿🇼"),
                d("Karanga Shona (Masvingo)", "Southern Zimbabwe", "🇿🇼"),
                d("Manyika Shona (Mutare)", "Eastern Zimbabwe", "🇿🇼"),
                d("Korekore Shona", "Northern Zimbabwe", "🇿🇼"),
                d("Ndau Shona", "Eastern Zimbabwe · Mozambique", "🇲🇿"),
            ),
        ),
        Language(
            "Lingala", "Lingála", "🇨🇩", Region.AFRICA, speakersMillions = 40,
            dialects = listOf(
                d("Kinshasa Lingala", "DR Congo", "🇨🇩"),
                d("Brazzaville Lingala", "Republic of the Congo", "🇨🇬"),
                d("Literary Lingala (Makanza)", "Upper Congo river", "🇨🇩"),
            ),
        ),
        Language(
            "Kinyarwanda", "Ikinyarwanda", "🇷🇼", Region.AFRICA, speakersMillions = 15,
            dialects = listOf(
                d("Kigali Kinyarwanda", "Rwanda", "🇷🇼"),
                d("Kirundi (Bujumbura)", "Burundi", "🇧🇮"),
                d("Rufumbira (Kisoro)", "Uganda", "🇺🇬"),
            ),
        ),
        Language(
            "Luganda", "Oluganda", "🇺🇬", Region.AFRICA, speakersMillions = 11,
            dialects = listOf(
                d("Kampala Luganda", "Buganda", "🇺🇬"),
                d("Rural Buganda Luganda (Masaka)", "Southern Buganda", "🇺🇬"),
            ),
        ),
        Language(
            "Wolof", "Wolof", "🇸🇳", Region.AFRICA, speakersMillions = 12,
            dialects = listOf(
                d("Dakar Wolof (urban)", "Senegal", "🇸🇳"),
                d("Saint-Louis Wolof", "Northern Senegal", "🇸🇳"),
                d("Baol Wolof (Touba, Diourbel)", "Central Senegal", "🇸🇳"),
                d("Gambian Wolof (Banjul)", "The Gambia", "🇬🇲"),
            ),
        ),
        Language(
            "Fula", "Fulfulde / Pulaar", "🇬🇳", Region.AFRICA, speakersMillions = 40,
            dialects = listOf(
                d("Pulaar (Fuuta Tooro, Senegal)", "Senegal · Mauritania", "🇸🇳"),
                d("Pular (Fuuta Jalon, Guinea)", "Guinea", "🇬🇳"),
                d("Maasina Fulfulde (Mopti)", "Mali", "🇲🇱"),
                d("Adamawa Fulfulde (Cameroon)", "Cameroon · Nigeria", "🇨🇲"),
                d("Nigerian Fulfulde (Sokoto)", "Nigeria", "🇳🇬"),
            ),
        ),
        Language(
            "Bambara", "Bamanankan", "🇲🇱", Region.AFRICA, speakersMillions = 15,
            dialects = listOf(
                d("Bamako Bambara", "Mali", "🇲🇱"),
                d("Ségou Bambara", "Central Mali", "🇲🇱"),
                d("Dioula (Bobo-Dioulasso, Abidjan)", "Burkina Faso · Côte d'Ivoire", "🇧🇫"),
                d("Malinké (Kankan)", "Guinea", "🇬🇳"),
            ),
        ),
        Language(
            "Akan / Twi", "Akan", "🇬🇭", Region.AFRICA, speakersMillions = 22,
            dialects = listOf(
                d("Asante Twi (Kumasi)", "Ashanti", "🇬🇭"),
                d("Akuapem Twi (Akropong)", "Eastern Region", "🇬🇭"),
                d("Fante (Cape Coast)", "Central Region", "🇬🇭"),
                d("Accra Twi (urban)", "Greater Accra", "🇬🇭"),
            ),
        ),
        Language(
            "Ewe", "Eʋegbe", "🇬🇭", Region.AFRICA, speakersMillions = 7,
            dialects = listOf(
                d("Ghanaian Ewe (Ho)", "Volta Region", "🇬🇭"),
                d("Togolese Ewe (Lomé)", "Togo", "🇹🇬"),
                d("Anlo Ewe (Keta)", "Coastal Ghana", "🇬🇭"),
            ),
        ),
        Language(
            "Malagasy", "Malagasy", "🇲🇬", Region.AFRICA, speakersMillions = 25,
            dialects = listOf(
                d("Merina Malagasy (Antananarivo)", "Highlands (standard)", "🇲🇬"),
                d("Betsileo Malagasy (Fianarantsoa)", "Southern highlands", "🇲🇬"),
                d("Sakalava Malagasy (Mahajanga)", "West coast", "🇲🇬"),
                d("Betsimisaraka Malagasy (Toamasina)", "East coast", "🇲🇬"),
                d("Antandroy Malagasy", "Deep south", "🇲🇬"),
            ),
        ),
        Language(
            "Berber / Tamazight", "ⵜⴰⵎⴰⵣⵉⵖⵜ", "🇲🇦", Region.AFRICA, speakersMillions = 25,
            dialects = listOf(
                d("Tashelhit (Agadir, Souss)", "Southern Morocco", "🇲🇦"),
                d("Central Atlas Tamazight (Khenifra)", "Middle Atlas, Morocco", "🇲🇦"),
                d("Tarifit (Nador, Rif)", "Northern Morocco", "🇲🇦"),
                d("Kabyle (Tizi Ouzou)", "Algeria", "🇩🇿"),
                d("Chaoui (Batna)", "Eastern Algeria", "🇩🇿"),
                d("Tuareg Tamasheq (Kidal, Agadez)", "Sahara", "🇲🇱"),
            ),
        ),

        // ───────────── Americas ─────────────
        Language(
            "Haitian Creole", "Kreyòl ayisyen", "🇭🇹", Region.AMERICAS, speakersMillions = 12,
            dialects = listOf(
                d("Port-au-Prince Creole", "Haiti", "🇭🇹"),
                d("Northern Haitian Creole (Cap-Haïtien)", "Northern Haiti", "🇭🇹"),
                d("Southern Haitian Creole (Les Cayes)", "Southern Haiti", "🇭🇹"),
                d("Miami / Brooklyn Haitian Creole", "Diaspora", "🇺🇸"),
            ),
        ),
        Language(
            "Jamaican Patois", "Patwa", "🇯🇲", Region.AMERICAS, speakersMillions = 3,
            dialects = listOf(
                d("Kingston Patois", "Jamaica", "🇯🇲"),
                d("Rural Jamaican Patois (St. Elizabeth)", "Western Jamaica", "🇯🇲"),
                d("London Jamaican", "Diaspora", "🇬🇧"),
            ),
        ),
        Language(
            "Quechua", "Runasimi", "🇵🇪", Region.AMERICAS, speakersMillions = 8,
            dialects = listOf(
                d("Cusco Quechua (Qusqu-Qullaw)", "Southern Peru", "🇵🇪"),
                d("Ayacucho Quechua (Chanka)", "Central-southern Peru", "🇵🇪"),
                d("Ancash Quechua (Huaraz)", "Central Peru", "🇵🇪"),
                d("Bolivian Quechua (Cochabamba)", "Bolivia", "🇧🇴"),
                d("Kichwa (Quito, Otavalo)", "Ecuador", "🇪🇨"),
                d("Santiago del Estero Quichua", "Argentina", "🇦🇷"),
            ),
        ),
        Language(
            "Guaraní", "Avañe'ẽ", "🇵🇾", Region.AMERICAS, speakersMillions = 7,
            dialects = listOf(
                d("Paraguayan Guaraní (Asunción)", "Paraguay", "🇵🇾"),
                d("Jopara (mixed Guaraní–Spanish)", "Urban Paraguay", "🇵🇾"),
                d("Corrientes Guaraní", "Argentina", "🇦🇷"),
                d("Mbyá Guaraní", "Paraguay · Brazil · Argentina", "🇧🇷"),
            ),
        ),
        Language(
            "Nahuatl", "Nāhuatl", "🇲🇽", Region.AMERICAS, speakersMillions = 2,
            dialects = listOf(
                d("Huasteca Nahuatl", "Hidalgo · Veracruz · San Luis Potosí", "🇲🇽"),
                d("Central Nahuatl (Puebla, Tlaxcala)", "Central Mexico", "🇲🇽"),
                d("Guerrero Nahuatl", "Guerrero", "🇲🇽"),
                d("Sierra Norte de Puebla Nahuatl", "Northern Puebla", "🇲🇽"),
                d("Classical Nahuatl", "Historical / literary", "🇲🇽"),
            ),
        ),
        Language(
            "Yucatec Maya", "Maayaʼ tʼàan", "🇲🇽", Region.AMERICAS, speakersMillions = 1,
            dialects = listOf(
                d("Yucatán Maya (Mérida, Valladolid)", "Yucatán", "🇲🇽"),
                d("Quintana Roo Maya", "Quintana Roo", "🇲🇽"),
                d("Campeche Maya", "Campeche", "🇲🇽"),
            ),
        ),
        Language(
            "K'iche'", "K'iche'", "🇬🇹", Region.AMERICAS, speakersMillions = 1,
            dialects = listOf(
                d("Central K'iche' (Quetzaltenango)", "Guatemala", "🇬🇹"),
                d("Nahualá K'iche'", "Sololá", "🇬🇹"),
            ),
        ),
        Language(
            "Aymara", "Aymar aru", "🇧🇴", Region.AMERICAS, speakersMillions = 2,
            dialects = listOf(
                d("La Paz Aymara", "Bolivia", "🇧🇴"),
                d("Puno Aymara", "Peru", "🇵🇪"),
                d("Chilean Aymara (Arica)", "Chile", "🇨🇱"),
            ),
        ),
        Language(
            "Mapudungun", "Mapudungun", "🇨🇱", Region.AMERICAS, speakersMillions = 1,
            dialects = listOf(
                d("Central Mapudungun (Temuco)", "Araucanía, Chile", "🇨🇱"),
                d("Pehuenche Mapudungun", "Andes", "🇨🇱"),
                d("Argentine Mapudungun (Neuquén)", "Argentina", "🇦🇷"),
            ),
        ),
        Language(
            "Navajo", "Diné bizaad", "🇺🇸", Region.AMERICAS, speakersMillions = 1,
            dialects = listOf(
                d("Central Navajo (Window Rock)", "Navajo Nation, Arizona", "🇺🇸"),
                d("Eastern Navajo (Shiprock)", "New Mexico", "🇺🇸"),
                d("Western Navajo (Tuba City)", "Arizona", "🇺🇸"),
            ),
        ),
        Language(
            "Inuktitut", "ᐃᓄᒃᑎᑐᑦ", "🇨🇦", Region.AMERICAS, speakersMillions = 1,
            dialects = listOf(
                d("South Baffin Inuktitut (Iqaluit)", "Nunavut", "🇨🇦"),
                d("Nunavik Inuktitut (Kuujjuaq)", "Northern Québec", "🇨🇦"),
                d("Inuinnaqtun (Cambridge Bay)", "Western Nunavut", "🇨🇦"),
                d("Kalaallisut (Greenlandic, Nuuk)", "Greenland", "🇬🇱"),
            ),
        ),
        Language(
            "Louisiana Creole", "Kouri-Vini", "🇺🇸", Region.AMERICAS, speakersMillions = 1,
            dialects = listOf(
                d("Louisiana Creole (St. Martin Parish)", "Louisiana", "🇺🇸"),
                d("Pointe Coupée Creole", "Louisiana", "🇺🇸"),
            ),
        ),
        Language(
            "Papiamento", "Papiamentu", "🇨🇼", Region.AMERICAS, speakersMillions = 1,
            dialects = listOf(
                d("Curaçao Papiamentu", "Curaçao", "🇨🇼"),
                d("Aruban Papiamento", "Aruba", "🇦🇼"),
                d("Bonaire Papiamentu", "Bonaire", "🇧🇶"),
            ),
        ),

        // ───────────── Oceania ─────────────
        Language(
            "Māori", "Te Reo Māori", "🇳🇿", Region.OCEANIA, speakersMillions = 1,
            dialects = listOf(
                d("Standard Māori (Waikato / Tainui)", "North Island", "🇳🇿"),
                d("Northern Māori (Ngāpuhi, Northland)", "Te Tai Tokerau", "🇳🇿"),
                d("Eastern Māori (Ngāti Porou, East Coast)", "Te Tai Rāwhiti", "🇳🇿"),
                d("Tūhoe Māori", "Te Urewera", "🇳🇿"),
                d("South Island Māori (Kāi Tahu)", "Te Waipounamu", "🇳🇿"),
            ),
        ),
        Language(
            "Hawaiian", "ʻŌlelo Hawaiʻi", "🇺🇸", Region.OCEANIA, speakersMillions = 1,
            dialects = listOf(
                d("Standard Hawaiian (Oʻahu)", "Hawaii", "🇺🇸"),
                d("Niʻihau Hawaiian", "Niʻihau", "🇺🇸"),
            ),
        ),
        Language(
            "Samoan", "Gagana Sāmoa", "🇼🇸", Region.OCEANIA, speakersMillions = 1,
            dialects = listOf(
                d("Samoan (Apia)", "Samoa", "🇼🇸"),
                d("American Samoan (Pago Pago)", "American Samoa", "🇦🇸"),
                d("New Zealand Samoan (Auckland)", "Diaspora", "🇳🇿"),
            ),
        ),
        Language(
            "Tongan", "Lea faka-Tonga", "🇹🇴", Region.OCEANIA, speakersMillions = 1,
            dialects = listOf(
                d("Tongan (Nukuʻalofa)", "Tonga", "🇹🇴"),
                d("Niuafoʻou Tongan", "Northern Tonga", "🇹🇴"),
            ),
        ),
        Language(
            "Fijian", "Na Vosa Vakaviti", "🇫🇯", Region.OCEANIA, speakersMillions = 1,
            dialects = listOf(
                d("Standard Fijian (Bau, Suva)", "Fiji", "🇫🇯"),
                d("Western Fijian (Nadi)", "Western Viti Levu", "🇫🇯"),
                d("Lau Fijian", "Lau Islands", "🇫🇯"),
            ),
        ),
        Language(
            "Tok Pisin", "Tok Pisin", "🇵🇬", Region.OCEANIA, speakersMillions = 4,
            dialects = listOf(
                d("Port Moresby Tok Pisin", "Papua New Guinea", "🇵🇬"),
                d("Highlands Tok Pisin", "PNG Highlands", "🇵🇬"),
                d("Islands Tok Pisin (Rabaul)", "New Guinea Islands", "🇵🇬"),
            ),
        ),
        Language(
            "Tahitian", "Reo Tahiti", "🇵🇫", Region.OCEANIA, speakersMillions = 1,
            dialects = listOf(
                d("Tahitian (Papeete)", "Tahiti", "🇵🇫"),
                d("Marquesan", "Marquesas Islands", "🇵🇫"),
                d("Tuamotuan", "Tuamotu Islands", "🇵🇫"),
            ),
        ),
        Language(
            "Bislama", "Bislama", "🇻🇺", Region.OCEANIA, speakersMillions = 1,
            dialects = listOf(
                d("Port Vila Bislama", "Vanuatu", "🇻🇺"),
                d("Luganville Bislama", "Espiritu Santo", "🇻🇺"),
            ),
        ),
    ).sortedByDescending { it.speakersMillions }

    private val byName: Map<String, Language> = buildMap {
        languages.forEach { l ->
            put(l.name.lowercase(), l)
            put(l.nativeName.lowercase(), l)
            l.name.split(" / ").forEach { put(it.trim().lowercase(), l) }
        }
    }

    /** Languages most people come here for, shown first in the picker. */
    val popular: List<Language> = languages.take(12)

    fun byRegion(): Map<Region, List<Language>> =
        Region.entries.associateWith { r -> languages.filter { it.region == r }.sortedBy { it.name } }

    /** Exact or case-insensitive match on the English or native name. */
    fun find(name: String): Language? = byName[name.trim().lowercase()]

    /** Canonical name for a free-text language, e.g. "portuguese" → "Portuguese". */
    fun canonicalName(name: String): String = find(name)?.name ?: name.trim()

    fun dialectsOf(language: String): List<Dialect> = find(language)?.dialects.orEmpty()

    fun defaultDialectFor(language: String): String = find(language)?.defaultDialect?.name ?: language.trim()

    /** Search across language names, native names, dialect names and regions. */
    fun search(query: String): List<SearchHit> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val hits = mutableListOf<SearchHit>()
        for (l in languages) {
            val nameHit = l.name.lowercase().contains(q) || l.nativeName.lowercase().contains(q)
            if (nameHit) hits += SearchHit(l, null, if (l.name.lowercase().startsWith(q)) 0 else 1)
            for (dlt in l.dialects) {
                val dHit = dlt.name.lowercase().contains(q) || dlt.region.lowercase().contains(q)
                if (dHit && !(nameHit && dlt.name.lowercase().removePrefix(q).isEmpty())) hits += SearchHit(l, dlt, 2)
            }
        }
        return hits.sortedWith(compareBy<SearchHit> { it.rank }.thenByDescending { it.language.speakersMillions })
    }

    data class SearchHit(val language: Language, val dialect: Dialect?, val rank: Int)
}
