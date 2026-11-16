# Pysyvä release-allekirjoitus

Repositorio ei sisällä avaimia tai salasanoja. Julkaisu estetään, kun jokin neljästä salaisuudesta puuttuu. Älä generoi uutta avainta työnkulussa. Ensimmäisen julkaisun avain ja applicationId `fi.omasali.app` ovat pysyviä.

## Kertaluonteinen käyttöönotto

1. Luo avain luotetulla koneella **repositorion ulkopuolelle**, salattuun turvalliseen hakemistoon. JDK:n keytool kysyy salasanan interaktiivisesti, joten salasana ei päädy komentohistoriaan:

   ```sh
   keytool -genkeypair -v -keystore oma-sali-release.jks -storetype JKS -alias oma-sali -keyalg RSA -keysize 4096 -validity 36500
   ```

2. Tee avaimesta erillinen salattu offline-varmuuskopio ja säilytä salasanat salasanojen hallinnassa. Varmista, että varmuuskopio voidaan avata. Avaimen katoaminen estää olemassa olevan asennuksen päivittämisen.
3. Aseta repositorion Settings → Secrets and variables → Actions → Repository secrets:

   - `ANDROID_KEYSTORE_BASE64`: avaintiedoston Base64-esitys.
   - `ANDROID_KEYSTORE_PASSWORD`: keystoren salasana.
   - `ANDROID_KEY_ALIAS`: `oma-sali`.
   - `ANDROID_KEY_PASSWORD`: avaimen salasana.

   `gh`-CLI:llä avaimen voi siirtää tulostamatta sitä konsoliin:

   ```sh
   base64 -w0 oma-sali-release.jks | gh secret set ANDROID_KEYSTORE_BASE64 --repo Tenuxi/Oma_sali_sovellus
   gh secret set ANDROID_KEYSTORE_PASSWORD --repo Tenuxi/Oma_sali_sovellus
   gh secret set ANDROID_KEY_ALIAS --repo Tenuxi/Oma_sali_sovellus
   gh secret set ANDROID_KEY_PASSWORD --repo Tenuxi/Oma_sali_sovellus
   ```

   Kolme viimeistä komentoa kysyvät arvon. macOS:n base64 ei tarvitse `-w0`-valitsinta.
4. Käynnistä **Android verification and release** Actions-välilehdellä main-haarasta.
5. Tallenna ensimmäisen APK:n sertifikaatin SHA-256-sormenjälki avainvarmuuskopion yhteyteen (`apksigner verify --print-certs APK`). Myöhemmät julkaisut vertaavat sertifikaattia edelliseen APK:hon automaattisesti.

Älä poista aiempia releaseja tai `build-*`-versionvaraustageja. Älä muuta salaisuuksia uudeksi avaimeksi. Pidä debug-asennus erillään varsinaisesta käytöstä: debug-avainta ei voi käyttää release-päivityksiin.

Paikallinen release-rakennus käyttää ympäristömuuttujia `KEYSTORE_FILE`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`, `VERSION_CODE` ja `VERSION_NAME`. Tavallisessa käytössä rakenna release vain Actionsissa, jotta versionvaraus ja vertailut suoritetaan.
