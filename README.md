# TramAgent — Android FUTÁR / GTFS demonstrációs alkalmazás

TramAgent egy Android-alkalmazás, amely a BKK FUTÁR / BKK GO API és a GTFS adatok felhasználásával valós idejű jármű- és menetrendi információkat jelenít meg.
A projekt célja egy többmodulos, jól átlátható demonstráció létrehozása, amely bemutatja a tömegközlekedési adatok feldolgozását Android-on.

##  Fő funkciók

A TramAgent több különálló demómodult tartalmaz:

### **FORGALMISZÁM KERESŐ (DK1) – Járművek megjelenítése adott viszonylaton**
- FUTÁR `vehicles-for-route` végpont használata  
- Forgalmi szám + rendszám kinyerése  
- Viszonylaton közlekedő összes jármű listázása

### **AKTÍV ZAVAROK (DK2) – Valós idejű zavarok (GTFS-RT Alerts)**
- GTFS-RT Alerts (`alerts.pb`) letöltése és feldolgozása  
- Csak villamosokra szűrt zavarok megjelenítése  
- Szolgáltatás-kiesés, késés, terelés, stb.

### **INDULÁSOK A KÖZELEMBEN (DK3) – Közeli megállók és indulások**
- A GTFS letöltése után egyszeri villamos-index készül (`tram_index.bin`), így a keresés másodpercek alatt lefut
- Csak a ma közlekedő menetek (calendar / calendar_dates), az éjfél utáni éjszakai indulásokkal együtt
- GPS alapú helymeghatározás  
- 300 méteren belüli megállók keresése (GTFS `stops.txt`)  
- Soron következő indulások szűrése 60 percen belül  
- Villamosokra szűrés (`route_type = 0 vagy 900`)

### **TÉRKÉPES KERESŐ (DK4) – Jármű pozíció térképen**
- OpenStreetMap (osmdroid), sötét színszűrővel, API-kulcs nélkül  
- Valós idejű jármű marker (villamos ikon)  
- Trip headsign (irány) megjelenítése  
- A trip összes megállójának kirajzolása GTFS adatokkal  
- Predicted / Scheduled időpontok kezelése

### **NYOMKÖVETÉS (DK5)– Részletes menetrend + késés számítása**
- TripId alapján FUTÁR `trip-details` használata  
- API-beli késés értelmezése (`arrivalDelay`)  
- Színkódolt késés kijelzés:
  - **piros** — nagy előfutás  
  - **zöld** — kisebb késés / normál tartomány  
  - **kék** — nagy késés  

### **GTFS Frissítő modul**
- Teljes budapesti GTFS ZIP letöltése  
  https://go.bkk.hu/api/static/v1/public-gtfs/budapest_gtfs.zip  
- ZIP kicsomagolása Android belső tárhelyre  
- Könyvtárstruktúra:

```
files/gtfs/
    stops.txt
    trips.txt
    stop_times.txt
    routes.txt
    ...
```

---

##  Projekt felépítése

```
app/
 ├─ data/            → BkkApi (FUTÁR/GO REST + GTFS loader), AlertsRepository
 ├─ domain/          → DK1–DK5 logikák, Futar (közös valós idejű lekérdezések), TramLines
 ├─ gtfs/            → GtfsUpdater (ZIP letöltés + kicsomagolás), GtfsRepository
 ├─ ui/theme/        → „Éjszakai BKK” téma (színek, betűk, pontmátrix font)
 ├─ ui/components/   → közös Compose elemek: villamos-animációk, kijelzős (LED) elemek, kártyák, oldalváz
 ├─ ui/<képernyő>/   → Compose képernyők (home, vehicles, alerts, nearby, map, tracking, gtfs)
 └─ ui/nav/          → navigáció „beálló szerelvény” átmenetekkel
```

Az architektúra szétválasztja:
- **UI réteg** (Jetpack Compose, egyetlen Activity + navigation-compose)  
- **Domain** (logika)  
- **Data** (API + fájl hozzáférés)

---

##  API-kulcs

A valós idejű funkciókhoz (DK1, DK2, DK4, DK5) saját **BKK FUTÁR API-kulcs** kell (ingyenes: https://opendata.bkk.hu/).
Két módon adható meg:

1. **Az appban:** főképernyő → fogaskerék → *BKK API-kulcs*. Mentés előtt ellenőrzi a kulcsot, és csak a készüléken tárolja.
2. **Buildkor:** a projekt gyökerében lévő (verziókezelésből kizárt) `local.properties` fájlban:

```properties
bkk.apiKey=SAJAT_BKK_FUTAR_KULCS
```

Az appban megadott kulcs elsőbbséget élvez. A térképhez (DK4) nem kell kulcs, mert OpenStreetMap-et használ (osmdroid).

---

##  Futtatás

1. Klónozd a repót:
   ```bash
   git clone https://github.com/AmerThamer/tram-agent.git
   ```
2. Nyisd meg Android Studio-ban  
3. Add meg a saját BKK API-kulcsot (az appban vagy a `local.properties`-ben)  
4. Futtasd a GTFS Frissítőt  
5. Teszteld a DK1–DK5 modulokat  



Licenc

MIT License
