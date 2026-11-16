# Toteutustilanne

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
- GitHub Actionsin ensimmäinen tarkistus: odottaa ajoa.
- Release-allekirjoitussalaisuudet puuttuvat repositoriosta (tarkistettu 16.11.2026). Julkaisu ei voi onnistua ennen niiden käyttöönottoa.
- Fyysisellä laitteella käynnistys, käytettävyys ja APK:n päivitysasennus: ei vielä testattu.
- Room v1 on ensimmäinen skeema. Vanhoja julkaistuja skeemoja/migraatioita ei ole; tulevat muutokset tarvitsevat migraation ja sen testin.

## Avoimet viimeistelyt / rajaukset

- Suurten historioiden sivutus ja käyrien zoomaus eivät kuulu nykyiseen toteutukseen.
- Käyttöliittymän manuaalinen tarkistus pienillä/suurilla näytöillä ja ruudunlukijalla tarvitaan.
- Ristiriidan valinta koskee kokonaista ohjelmaa/treeniä, ei yksittäistä sarjaa.
- Kokorajat: 32 Mt varmuuskopio, 12 Mt alkuperäiskuva, 300 kt tallennettu JPEG.

Tätä ei merkitä valmiiksi julkaistuksi v1.0:ksi ennen onnistunutta rakennusta, pysyvää allekirjoitusta ja laitevarmennusta.
