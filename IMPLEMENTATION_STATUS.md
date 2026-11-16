# Toteutustilanne

## v1.2 käytettävyys- ja luotettavuuspäivitys

- Tiivistetty kaikkien viiden näkymän hierarkiaa, välejä ja rivejä. Sarjakentät ovat aiempaa matalampia mutta kosketusalueet vähintään 48 dp.
- Yhteinen WorkoutSummary määrää etusivun/treenin sarja- ja liikemäärät sekä palkin. Valmistuminen perustuu tavoitesarjoihin. Päättynyt ja kokonaan suoritettu treeni erotetaan.
- Palkin vanha laskenta oli jo nolla tapauksessa 0/3. Material 3:n päätepistemerkki aiheutti kuitenkin harhaanjohtavan vihreän pisteen oikealle: korvattu palkilla, joka ei piirrä vihreää nollassa.
- Kuvista puuttuvat pysyvät harjoitus- ja liike-esiintymien tunnisteet, eikä puhelimen tietokantaa ole saatavilla. Siksi kuvien 0/2-sarjan eroa tai samannimisiä liikkeitä ei oleteta samaksi tietueeksi. Regressiotesti käyttää todistetusti samaa treeni-ID:tä ja muokkaa historiaa etusivun ollessa auki. Samannimiset liikkeet erotellaan järjestysnumeroin ja aktiivinen esiintymä suodatetaan tunnisteella.
- Vanha syöttöluonnos ei varjosta korjattua Room-arvoa. Poikkeava luonnos säilyy käyttäjän palautettavaksi; syötteitä ei muuteta arvaamalla.
- Yksi mittapiste esitetään kompaktisti, nolla pistettä yhtenä tyhjänä tilana ja vähintään kaksi kuvaajana. Painoarvot säilyvät koskemattomina.
- Ei Room-migraatiota, datan resetointia tai varmuuskopioformaatin muutosta. ApplicationId ja release-avain ovat samat.
- Kohdennetut yhteenveto-, luonnos-, volyymi-/ennätys- ja Compose-regressiotestit mukana Actionsissa. Kuvakaappaukset myös 320 dp leveydeltä molemmissa teemoissa. Viimeisin todellinen tulos: [Actions](https://github.com/Tenuxi/Oma_sali_sovellus/actions).

## v1.1-käyttöliittymäpäivitys

- UI-001: tiivis liikekirjasto ja toimintovalikot; ohjelman yhden päivän muokkaus ja skrollattava päivänvalinta.
- UI-002: yksi avoin liike, tiiviit sarjarivit, tehdyt/ohitetut listat, eteneminen, seuraava liike/yhteenveto. Avoin liike ja syöttöluonnokset tallentuvat DataStoreen.
- UI-003: yksi ensisijainen aloitus, muiden päivien valinta, keskeneräiset ja oikeasta datasta laskettu edellisen treenin yhteenveto.
- UI-004: Liikkeet/Kehonpaino, yksi valittu mittari ja kuvaaja, tarkka pistevalinta sekä pysyvät valinnat.
- VIS-001: nykyisen tumman/vaalean Material 3 -teeman tiiviit rivit, saavutettavat kenttien nimet, vähintään 48 dp kosketuskohteet, vaakavieritettävät valinnat.
- Room-skeema ja historialliset tietomallit säilyvät ennallaan; ei uutta migraatiota. Sama applicationId ja release-sertifikaatti.
- Lisätty kohdennetut DataStore-uudelleenavaus-, syöttö-, valmistumis-, kuvaajadata- ja Compose-käyttöliittymätestit. Compose-testi tuottaa kuvakaappaukset CI:n raporttiartifaktiin. Viimeisin todellinen tarkistus- ja julkaisutilanne: [GitHub Actions](https://github.com/Tenuxi/Oma_sali_sovellus/actions).
- Fyysisen puhelimen käytettävyys ja uuden version asentaminen olemassa olevan päälle ovat käyttäjän laitteella vielä varmennettavia.

## Toteutettu lähdekoodiin

- Android-projekti, Compose/Material 3, viisi suomenkielistä päänäkymää, teemat ja näytön päälläpito.
- Room, Flow, ViewModel/Repository, sarjoitettu automaattitallennus, tarkat grammamäärät, DataStore laiteasetuksille.
- Liikekirjasto, haku ja lihasryhmäsuodatus, omat liikkeet, galleriakuvien yksityinen tallennus.
- 2-/3-jakoiset ja tyhjät ohjelmat, päivät, tavoitteet, ohjelmien/päivien/liikkeiden järjestys, aktiivinen ohjelma.
- Treenikerran historiakopiot, useat keskeneräiset treenit, aloituspohjainen kierto ja peruminen.
- Sarjojen muokkaus, lisääminen/poisto, liikkeiden vaihto/ohitus/järjestys, muistiinpanot, historian ja aikojen korjaus.
- Ulkoinen paino, kehonpaino, erillinen lisäpaino ja avustus; edelliset suoritukset ja ehdot täyttävät korotusehdotukset.
- Kehityskäyrät, volyymi, rajattu Epley-arvio, painoennätykset, punnitusten CRUD ja käyrä.
- Versionoitu JSON-varmuuskopio, esikatselu, revisiopohjaiset ristiriidat, poistomerkinnät, idempotenssi ja atominen tuonti kuvineen.
- Actions-tarkistus ja pysyvää avainta edellyttävä release, monotoninen versionvaraus ja edelliseen sertifikaattiin vertailu.
- Kohdennetut laskenta-, kierto-, Room-pysyvyys- ja yhdistämistestit.

## Varmennus ja julkaisu

- Paikallista Gradle-rakennusta tai emulaattoria ei ajettu käyttäjän ohjeen mukaisesti.
- GitHub Actions: testit, lint-tarkistus ja debug-APK:n rakennus on läpäisty. V2-migraation sisältävä ajo 43835656032 läpäisi kaikki 12 testiä. Viimeisimmän commitin tulos näkyy [Actionsissa](https://github.com/Tenuxi/Oma_sali_sovellus/actions). SDK-valmistelun vanhentunut tools-oletus korjattiin.
- Lisätty kuvan/poistomerkinnän palautustesti, kehonpainon historiatestit ja suurten tietueiden migraatiotesti. Ne läpäisivät Actions-tarkistuksen. Lisäksi mukana on Unicode-merkkien pilkkomisrajan testi.
- Pysyvä RSA-4096 / PKCS12 -release-avain ja kaikki neljä GitHub Secrets -arvoa otettu käyttöön 16.11.2026. Salattu avainvarmuuskopio on repositorion ulkopuolella. Julkaisun sertifikaatti tarkistetaan versionhallinnassa olevaan julkiseen sormenjälkeen.
- Työnkulku rakentaa ladattavaksi vain release-APK:n. Varsinaiset julkaisut: [GitHub Releases](https://github.com/Tenuxi/Oma_sali_sovellus/releases/latest).
- Fyysisellä laitteella käynnistys, käytettävyys ja APK:n päivitysasennus: ei vielä testattu.
- Room v2 pilkkoo suuret aggregaatit turvallisiin osiin. V1 → v2 -migraatio ja yli 2 Mt tietueen säilymistesti on toteutettu. V1- ja v2-skeemat ovat versionhallinnassa.

## Avoimet viimeistelyt / rajaukset

- Suurten historioiden sivutus ja käyrien zoomaus eivät kuulu nykyiseen toteutukseen.
- Käyttöliittymän manuaalinen tarkistus pienillä/suurilla näytöillä ja ruudunlukijalla tarvitaan.
- Ristiriidan valinta koskee kokonaista ohjelmaa/treeniä, ei yksittäistä sarjaa.
- Kokorajat: 32 Mt varmuuskopio, 12 Mt alkuperäiskuva, 300 kt tallennettu JPEG.

Pysyvä allekirjoitus on määritetty ja automaattinen release-julkaisu käytössä. Fyysisen laitteen käytettävyys- ja päivitysvarmennus on vielä tehtävä.
