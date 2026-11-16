# OMA SALI

## v1.2: tiiviimpi kirjaus ja yhtenäiset yhteenvedot

Sarjakirjaus käyttää matalia numerokenttiä 48 dp kosketusalueilla. Treenin otsikko näyttää erikseen valmiit liikkeet ja kuitatut sarjat. Palkissa ei ole harhaanjohtavaa vihreää päätepistettä. Liikkeet numeroidaan harjoituksen järjestyksen mukaan: saman kirjastoliikkeen eri esiintymät erottuvat toisistaan, mutta aktiivista WorkoutExercise-tunnistetta ei näytetä suljetuissa listoissa.

Yhden mittapisteen Kehitys-näkymä näyttää arvon ja ajankohdan ilman kuvaajaa tai pisteiden selauspainikkeita. Kahdesta pisteestä alkaen näkyy kuvaaja, kosketettava pistevalinta ja näppäimistöllä/ruudunlukijalla käytettävät selauspainikkeet. Käyttäjän kirjaamia painoja (esimerkiksi 3 kg) ei korjata automaattisesti.

### Laskentakäsitteet

- Kuitattu sarja: Roomiin tallennettu `done=true`-sarja ohittamattomassa liikkeessä. Käyttöliittymän keskeneräinen luonnos ei ole suoritus.
- Valmis liike: ensimmäiset `target.sets` sarjaa ovat olemassa ja kuitattu, eikä liike ole ohitettu. Pelkkä rivin poisto ei pienennä tavoitetta. Harjoituskohtaisen tavoitteen voi muuttaa liikkeen valikosta; ohjelma ei muutu. Tavoitteen jälkeiset lisäsarjat ovat vapaaehtoisia mutta kuitattuina mukana sarjamäärässä ja tilastoissa.
- Päättynyt treeni: `status=FINISHED`; myös osittainen treeni voidaan päättää. Tämä ei kuittaa puuttuvia sarjoja tai liikkeitä.
- Edistyminen: valmiit liikkeet / kaikki harjoituksen liikkeet. Ohitetut näytetään erikseen eivätkä ole valmiita. Sarjaluvun nimittäjä sisältää ohittamattomien liikkeiden sarjat tai tavoitemäärän, jos rivejä on poistettu tavoitetta pienemmäksi.
- Etusivun koonti: viimeisin päättyneeksi merkitty treenikerta lopetusajan perusteella. Kortti ja treeninäkymä käyttävät samaa `summarize`-funktiota, samoja pysyviä tunnisteita ja Roomin reaktiivista Flow-tilaa. Kortin napautus avaa juuri tämän treenin.
- Kehityspiste: kyseisen liikkeen kuitatut, ohittamattomat sarjat harjoituksessa; mukana myös keskeneräiset mutta ei perutut harjoitukset. Päivä ja kellonaika ovat harjoituksen käyttäjän korjattavasta aloitusajasta. Ei erillistä vanhenevaa tilastovälimuistia.

Vanha kenttäluonnos ei peitä myöhemmin korjattua tietokannan arvoa. Jos tallennettu arvo ja aiempi luonnos eroavat, vanha syöte säilyy palautettavaksi sarjavalikosta. Tallennusta odottava syöte on merkitty erikseen. V1.1:n käyttöliittymäasetukset luetaan edelleen; Room-skeema ja varmuuskopioformaatti eivät muutu.

## Käyttöliittymä v1.1

Etusivulla on yksi seuraavan päivän aloituspainike, muiden päivien valitsin sekä keskeneräisten ja viimeisimmän valmistuneen treenin yhteenveto. Liikekirjastossa on tiiviit rivit, lihasryhmävalinnat ja toimintovalikot. Ohjelmasta muokataan yhtä päivää kerrallaan.

Treenissä vain yksi liike on avoinna. Muut liikkeet, tehdyt liikkeet ja ohitetut liikkeet ovat erillisissä tiiviissä listoissa. Avoin liike muistetaan treenikertakohtaisesti. Sarjojen numerokentät ja keskeneräisetkin syötteet säilytetään DataStoressa; kelvolliset sarjat tallennetaan entiseen tapaan Roomiin. Virheellistä syötettä ei lasketa suoritukseksi. Tilastot perustuvat edelleen vain Roomiin tallennettuihin kuitattuihin sarjoihin.

Kehitys-näkymässä valitaan Liikkeet/Kehonpaino, liike, mittari ja aikaväli. Näkyvissä on yksi kuvaaja. Pistettä voi koskettaa tai valita Edellinen/Seuraava piste -painikkeilla. Valinnat muistetaan sovelluksen sulkemisen jälkeen. Käyttöliittymän navigointitila ja kenttäluonnokset ovat laitekohtaisia eivätkä muuta JSON-varmuuskopioformaattia. Varmuuskopion onnistunut palautus tyhjentää vanhat kenttäluonnokset, jotta ne eivät peitä palautettuja sarja-arvoja.

Päivitys ei muuta Room-skeemaa (v2), sovelluksen tunnistetta tai release-avainta. Asenna uusi release-APK vanhan päälle poistamatta sovellusta.

Suomenkielinen, kokonaan offline toimiva Android-treenipäiväkirja henkilökohtaiseen käyttöön. Sovelluksessa ei ole käyttäjätiliä, verkkolupaa, mainoksia tai analytiikkaa. Pysyvä applicationId on **fi.omasali.app**. Android 8.0 (API 26) tai uudempi.

## Käyttö

1. Avaa **Ohjelmat** ja luo 2- tai 3-jakoinen pohja tai tyhjä oma ohjelma. Muokkaa päivät, liikejärjestys, sarjat ja toistoalueet. Valitse aktiivinen ohjelma.
2. **Liikekirjastossa** voit lisätä liikkeitä, kuvia ja laitteita, muuttaa painonkorotusta sekä valita kuormitustavan.
3. Aloita päivä **Etusivulta**. Kierto etenee heti aloituksessa. Useita treenejä voi olla kesken. Peruminen poistaa vain kyseisen aloituksen vaikutuksen.
4. Kirjaa sarjojen painot ja toistot ja kuittaa **Tehty**. Kelvolliset muutokset tallentuvat automaattisesti. Virheellinen keskeneräinen kenttä ilmoitetaan eikä sitä tallenneta. Näkymästä poistuminen jättää treenin kesken; **Lopeta treeni** päättää sen.
5. **Treenit** näyttää keskeneräiset ja päättyneet treenit. Ajankohta, sarjat ja muistiinpanot ovat korjattavissa. Ohjelman muutokset eivät muuta treenikerran kopiota.
6. **Kehitys** näyttää tallennetusta datasta lasketut käyrät ja punnitukset. Tilastoissa ovat vain kuitatut, ohittamattomien liikkeiden sarjat, myös keskeneräisistä treeneistä. Epley-arvio koskee vain ulkoista painoa ja 1–12 toiston sarjoja.
7. **Asetukset** tarjoaa vaalean/tumman teeman, näytön päälläpidon ja varmuuskopiot.

## Varmuuskopiointi

Vie JSON Androidin tiedostovalitsimella turvalliseen paikkaan. Tiedosto sisältää henkilötietoja eikä ole salattu. Mukana ovat ohjelmat, liikkeet kuvineen, treenit, sarjat, kierto, punnitukset, asetukset ja poistomerkinnät.

Tuonti tarkistaa version, koon, tunnisteet, tietotyypit, viitteet ja arvot. Esikatselussa näkyvät lisäykset, päivitykset ja ristiriidat. Valitse ristiriidassa nykyinen tai varmuuskopion versio ja vahvista. Tuonti yhdistää; se ei tyhjennä tietokantaa. Sama varmuuskopio ei luo duplikaatteja. Jos nykydata muuttuu esikatselun aikana, tuonti estetään ja esikatselu pitää avata uudelleen. Ohjelman ja sen liikkeiden ristiriidat on ratkaistava keskenään johdonmukaisesti.

Yhdistämisyksikkö on kokonainen ohjelma, liike, treenikerta, punnitus tai asetukset. Yksittäisiä saman treenin sarjoja ei yhdistetä automaattisesti eri haaroista. Muutosrevisioiden esi-isät erottavat vanhat kopiot ristiriidoista ilman laitekelloihin luottamista. Poistomerkinnät säilyvät. Kuvat ovat sovelluksen yksityiseen Room-tietokantaan tallennettuja, enintään 300 kt JPEG-kuvia; myös ne palautuvat samassa transaktiossa. Tuonnin ja viennin kokoraja on 32 Mt. Gallerian alkuperäiskuvan lukuraja on 12 Mt; kuva pienennetään enintään noin 800 pikselin sivumittaan.

## Teknologiat ja rakenne

Kotlin 2.2.20, Jetpack Compose / Material 3, Navigation Compose, Room 2.8.4, DataStore Preferences, Coroutines/Flow ja MVVM/Repository. Gradle 8.13, AGP 8.13.2, JDK 17. Riippuvuudet on lukittu vakaisiin versioihin. Yhteensopivuuden lähteet: [AGP](https://developer.android.com/build/releases/agp-8-13-0-release-notes), [Compose compiler](https://developer.android.com/jetpack/androidx/releases/compose-kotlin), [Compose BOM](https://developer.android.com/develop/ui/compose/bom), [Room](https://developer.android.com/jetpack/androidx/releases/room).

- `Model.kt`: tyypitetyt tietomallit, kierto, mittarit, korotusehdotukset ja yhdistämisen vertailu.
- `Database.kt`, `Repository.kt`: Room-tallennus, transaktiot ja historiakopiot.
- `Validation.kt`: tuonnin ja tallennusten tarkistukset.
- `MainActivity.kt`: ViewModel ja sarjoitettu tallennusjono.
- `Ui.kt`, `Statistics.kt`, `BackupUi.kt`: suomenkieliset näkymät.
- `app/src/test`: laskenta-, Room-pysyvyys- ja varmuuskopiotestit.
- `.github/workflows/android.yml`: tarkistus ja julkaisu.
- `scripts/`: version varaaminen ja APK-tarkistus.

Room sisältää versionoidut JSON-aggregaatit pysyvillä tunnisteilla. Ne tallennetaan 64 000 merkin osiin, jotta useita kuvia sisältävä treenikerta ei ylitä Androidin CursorWindow-lukurajaa. Päivät ja tavoiteliikkeet kuuluvat ohjelma-aggregaattiin, suoritukset ja sarjat treeniaggregaattiin. Kiertotapahtuma kuuluu treenikertaan (muuttumaton `cycleOrder` ja päivän ID); päivämäärän korjaus ei muuta kiertojärjestystä. Historiassa ei ole poistokaskadeja ohjelmiin tai liikekirjastoon. DataStore tallentaa laitekohtaisen viimeisen vientiajan; varmuuskopioitavat asetukset ovat Roomissa, jotta palautus on atominen.

## Android Studio ja paikallinen rakentaminen

Avaa projektihakemisto Android Studiossa. Käytä JDK 17:ää ja asenna Android SDK 36. Anna Gradle Syncin hakea riippuvuudet. SDK-polku asetetaan paikalliseen `local.properties`-tiedostoon (`sdk.dir=...`), jota ei commitoida.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Debug-APK löytyy `app/build/outputs/apk/debug/app-debug.apk`. Se on vain kehityskäyttöön ja eri avaimella allekirjoitettu kuin release. Älä käytä sitä pysyvän treenihistorian ylläpitoon tai yritä asentaa release-version päälle. Paikallisia Gradle-käännöksiä ja emulaattoria ei tarvita tavallisten muutosten yhteydessä; ensisijainen tarkistus on GitHub Actions.

## GitHub Actions ja julkaisut

Main-push ja manuaalinen ajo suorittavat kohdennetut testit ja release-version Android lintin. Ladattavaksi rakennetaan vain pysyvällä avaimella allekirjoitettu release-APK. PR-ajot eivät pääse allekirjoitussalaisuuksiin. Tarkistusraportit ja Room-skeema tallennetaan Actions-artifakteiksi. Kehitys-APK:ta ei julkaista.

Release suoritetaan vain main-haaralla onnistuneen tarkistuksen jälkeen. Pysyvä RSA-4096-allekirjoitusavain ja neljä salaisuutta on otettu käyttöön 16.11.2026; katso [allekirjoitusohje](docs/SIGNING.md). Puuttuva salaisuus aiheuttaa selkeän virheen eikä releasea luoda.

VersionCode varataan GitHubin pysyvään `build-N`-tagiin ennen rakennusta. Yhteinen concurrency-lukko kattaa koko tarkistus- ja julkaisuprosessin. Vanhentuneen main-commitin julkaisu estetään. Uudelleenajo varaa uuden numeron, epäonnistuminen jättää aukon, eikä laskuri riipu työnkulun nimestä tai kellosta. **Älä poista tai siirrä `build-*`-tageja.** GitHub voi korvata jonossa odottavan ajon uudemmalla main-pushilla; käynnissä olevaa julkaisua ei keskeytetä. Uusimman ajon lähdekoodi sisältää aiemmat muutokset.

VersionName käyttää v1.2-päivityksestä lähtien muotoa `1.2.N` ja kasvaa versionCode-varauksen mukaan (versionCode 4 vastaa versiota 1.2.0). Rakennuksen jälkeen `apksigner` tarkistaa allekirjoituksen ja `aapt` pakettitunnuksen, version ja debug-lipun. Edellisen julkaistun APK:n sertifikaattiin ja versionCodeen verrataan ennen julkaisua. Vasta sitten syntyy `v1.0.N`-tagi ja **OMA-SALI-v1.0.N.apk** GitHub Releaseen. Työnkulku ei tee main-committeja eikä käynnistä itseään.

## Asennus ja päivitys puhelimeen

Lataa allekirjoitettu APK projektin [Releases-sivulta](https://github.com/Tenuxi/Oma_sali_sovellus/releases). Avaa tiedosto Androidissa ja salli tarvittaessa asennus käyttämältäsi tiedostosovellukselta. Seuraavat versiot asennetaan vanhan päälle **poistamatta sovellusta**. Sama applicationId, sama allekirjoitusavain ja kasvava versionCode ovat välttämättömiä. Ota varmuuskopio ennen päivitystä. Sovelluksen poistaminen tyhjentää paikalliset tiedot.

## Skeema ja rajoitukset

Room-skeema on v2. Kehitysversion v1-tietueet pilkotaan tietoja säilyttävällä SQL-migraatiolla. V1-skeema säilytetään lähdekoodissa, ja migraatiotesti kattaa yli 2 Mt tietueen sekä sen myöhemmän pienentämisen. Destructive migrationia ei käytetä. Tuleva skeemamuutos edellyttää eksplisiittistä migraatiota, vanhan skeeman säilyttämistä ja migraatiotestiä. Myös JSON-payloadin muutokset tarvitsevat yhteensopivuuden tai formaattimigraation; älä poista serialisoituja kenttiä huomaamatta.

Kuvien ja varmuuskopioiden kokorajat ovat tarkoituksellisia. Suuri historia luetaan tällä hetkellä muistissa Flow-tilaksi; erittäin laajalle aineistolle tarvitaan sivutus. Varmuuskopion ristiriidan ratkaisu on aggregaattikohtainen. Käyrät ovat yksinkertaisia eivätkä tarjoa zoomausta. Fyysisen Android-laitteen käytettävyys ja päivitysasennus on vielä tarkistettava ensimmäisen allekirjoitetun APK:n valmistuttua. Ajantasaiset varmennetut tulokset ja avoimet asiat: [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md).
