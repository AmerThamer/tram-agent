# TramAgent — Android FUTÁR / GTFS demonstrációs alkalmazás

**TramAgent** egy Android-alkalmazás, amely a **BKK FUTÁR / BKK GO API** és a **GTFS** adatok felhasználásával valós idejű jármű- és menetrendi információkat jelenít meg.  


---

## Fő funkciók

A TramAgent több különálló demómodult tartalmaz:

### **DK1 – Járművek megjelenítése adott viszonylaton**
- FUTÁR „vehicles-for-route” lekérdezés
- Forgalmi szám + rendszám megjelenítése

### **DK2 – Valós idejű zavarok (Alerts.pb)**
- GTFS-RT Alerts feldolgozása
- Villamosokra szűrt zavarok listázása

### **DK3 – Közeli megállók és indulások**
- GPS-koordináta alapján 300 m-en belüli megállók
- Következő 60 perc indulásai

### **DK4 – Jármű pozíció térképen**
- Google Maps integráció
- Valós idejű jármű marker (villamos ikon)
- Útvonal fejléce (headsign) megjelenítése
- Trip megállóinak kirajzolása térképre

### **DK5 – Részletes jármű menetrend + késés számítás**
- TripId alapján megállók és érkezési időpontok
- API-beli késés kiértékelése (színkódolva)

### **GTFS Frissítő**
- Teljes budapesti GTFS csomag letöltése
- Belső tárhelyen kibontja: `stops.txt`, `trips.txt`, `stop_times.txt`, stb.

---


