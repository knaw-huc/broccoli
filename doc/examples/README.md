# Voorbeelden van gebruik van 🥦 links

## Ophalen data voor bijv. TextAnnoViz

#### Vind de relevante `anno`, `text`, en `iiif` onderdelen voor brief `urn:mondriaan:letter:14569`

* https://broccoli.tt.di.huc.knaw.nl/projects/mondriaan/urn:mondriaan:letter:14569?includeResults=anno,text,iiif

Het antwoord van broccoli is samengesteld:

* `anno` is de betreffende annotatie uit AnnoRepo;
* `text` is het relevante stuk 'segmented text' en komt uit TextRepo;
* `iiif` betreft de scan / het plaatje en komt uit annotaties en gaat over een externe iiif server

(`profile` en `request` in het resultaat kun je negeren: profiling resp. debug info over het request en de uitvoering ervan)

Dit endpoint kan ook overlappende annotaties vinden. 

#### Hiermee vind je bijv. alle `tei:P` paragrafen uit deze brief:

* https://broccoli.tt.di.huc.knaw.nl/projects/mondriaan/urn:mondriaan:letter:14569?includeResults=anno,text,iiif&overlapTypes=tei:P

Het stukje `anno` bevat nu 5 resultaten, waarin de paragraaf annotaties staan.

Ook kun je de locaties van de tekstsegmenten laten herberekenen relatief ten opzichte van de opgevraagde annotatie.

#### Vind de `tei:Rs` elementen in paragraaf `p:14785` en geef hun locaties relatief t.o.v. het omvattende `tei:P` element:
* https://broccoli.tt.di.huc.knaw.nl/projects/mondriaan/urn:mondriaan:p:14785?overlapTypes=tei:Rs&relativeTo=tei:P

Bespreek hierbij het `text.locations` gedeelte.

Bijvoorbeeld `tei:Rs` element `"de schedelmeter"` met id `urn:mondriaan:rs:15156` is na herberekening terechtgekomen op regels 3 t/m 6 in het omvattende `tei:P` element.

```curl -LSs 'https://broccoli.tt.di.huc.knaw.nl/projects/mondriaan/urn:mondriaan:p:14785?overlapTypes=tei:Rs&relativeTo=tei:P' | jq .text.locations.annotations[0]
{
  "bodyId": "urn:mondriaan:rs:15156",
  "start": {
    "line": 3
  },
  "end": {
    "line": 6
  }
}
```

Hier zijn de coordinaten van (stukje textrepo) "de schedelmeter"
https://mondriaan.tt.di.huc.knaw.nl/textrepo/view/versions/9bae62d9-87fb-46e5-8b0b-275554000a35/segments/index/1139/1142

herberekend binnen de brief waar ze op posities 140-144 stonden
```
$ curl -LSs https://broccoli.tt.di.huc.knaw.nl/projects/mondriaan/urn:mondriaan:letter:14569?includeResults=text | jq .text.lines[140,141,142,143]
"de "
"\""
"schedelmeter"
"\" "
```


#### Ditto voor (alle) andere types, Textannoviz haalt bijv.  nu alle bestaande annotatie types op en laat alle coördinaten van de tekstsegmenten door 🥦 herberekenen t.o.v. de omvattende brief.
* https://broccoli.tt.di.huc.knaw.nl/projects/mondriaan/urn:mondriaan:letter:14569?overlapTypes=tei:Add,tei:Address,tei:Addrline,tei:Altidentifier,tei:Body,tei:C,tei:Change,tei:Choice,tei:Closer,tei:Correspaction,tei:Correspdesc,tei:Country,tei:Date,tei:Dateline,tei:Decodesc,tei:Deconote,tei:Del,tei:Div,tei:Editor,tei:Facsimile,tei:Filedesc,tei:Graphic,tei:Hi,tei:Idno,tei:Institution,tei:Msdesc,tei:Msidentifier,tei:Name,tei:Note,tei:Objectdesc,tei:Opener,tei:Orig,tei:P,tei:Physdesc,tei:Placename,tei:Postmark,tei:Postscript,tei:Profiledesc,tei:Ptr,tei:Publicationstmt,tei:Ref,tei:Reg,tei:Revisiondesc,tei:Rs,tei:Salute,tei:Settlement,tei:Signed,tei:Sourcedesc,tei:Space,tei:Sponsor,tei:Surface,tei:Teiheader,tei:Text,tei:Title,tei:Titlestmt,tei:Unclear,Folder,Letter,Page,Sentence&includeResults=anno,text,iiif&relativeTo=Letter

---

## URL schema op basis van tiers (i.c. `folder` en `letter`)

(vergelijk `volume` en `opening` bij Republic, `document` en `opening` bij Globalise)

### Vind de `Letter` die hoort bij `folder`=`proeftuin` en `letter`=`19090421y_IONG_1304`
  
  * https://broccoli.tt.di.huc.knaw.nl/projects/mondriaan/Letter/proeftuin/19090421y_IONG_1304?includeResults=bodyId
  
```curl -LSs https://broccoli.tt.di.huc.knaw.nl/projects/mondriaan/Letter/proeftuin/19090421y_IONG_1304?includeResults=bodyId | jq
{
  "request": {
    "projectId": "mondriaan",
    "bodyType": "Letter",
    "tiers": [
      "proeftuin",
      "19090421y_IONG_1304"
    ],
    "includeResults": [
      "bodyId"
    ]
  },
  "bodyId": "urn:mondriaan:letter:14569"
}
```

Nu weten we dat het ```bodyId``` van brief ```19090421y_IONG_1304``` is: ```urn:mondriaan:letter:14569```

Je kunt ook meteen die (in dit geval ```Letter```) annotatie erbij opvragen, door ```anno``` toe te voegen aan de URL:

https://broccoli.tt.di.huc.knaw.nl/projects/mondriaan/Letter/proeftuin/19090421y_IONG_1304?includeResults=anno,bodyId

```curl -LSs https://broccoli.tt.di.huc.knaw.nl/projects/mondriaan/Letter/proeftuin/19090421y_IONG_1304?includeResults=anno,bodyId | jq
{
  "request": {
    "projectId": "mondriaan",
    "bodyType": "Letter",
    "tiers": [
      "proeftuin",
      "19090421y_IONG_1304"
    ],
    "includeResults": [
      "anno",
      "bodyId"
    ]
  },
  "bodyId": "urn:mondriaan:letter:14569",
  "anno": [
    {
      "purpose": "tagging",
      "generated": "2023-04-21T09:27:33.510216",
      "type": "Annotation",
      "body": {
        "id": "urn:mondriaan:letter:14569",
        "type": "Letter",
        "tt:textfabric_node": 14569,
        "text": "Brief aan Aletta de Iongh. Amsterdam, tussen maandag 19 en vrijdag 23 april 1909.\nWietse Coppes\nLeo Jansen\nMondriaan Editieproject\n\nNederland\nOtterlo\nKröller Müller Museum\nKM 120.999\n19090421y_IONG_1304\n\n\n\nPiet Mondriaan\ntussen maandag 19 en vrijdag 23 april 1909\nAmsterdam[?]\nAletta de Iongh\n\ntranscriptie: voltooid 21.7.15\ncollatie bron: 6.6.16\ntweede collatie: voltooid 5.8.16\ninvoer tweede collatie: voltooid 5.8.16\nbespreking eindversie: gb\nmarkeren annotaties: in bewerking / voltooid\ngereed 17.4.2019\ntitel gecontroleerd 12.10.2020\npersonen getagd 12.10.2020\ncodering personen aangepast 16.2.2022\n\n\n\n\n\n\n\nBeste Zus,\nVan morgen was de \"schedelmeter\" bij me, hij zag de schetsen naar jouw en vond je voorhoofd zoo mooi en de uitdrukking van je oogen, dat hij graag een photo van je woû opnemen. Hij komt Zondag nog hier. Heb je tijd en ideé, kom dan om 10 uur bij me, anders schrijf ik hem maar dat je niet kunt, in geval je er af wilt wezen. 't Zal wel een tijd duren eer je een afdruk van hem krijgt en misschien zet hij je later in een boek: ik waarschuw je   je maarmaar , dan kun je doen zooals je wilt.\nLaten we zoo afspreken, als je geen idée of tijd hebt, schrijf me dan 't even en kom anders Zondag morgen om 10 uur bij me.\nDag beste lieve Zus, je Piet.\n\nManuscript.\nDe brief dateert van vóór brief MEP_1293 van circa 13 mei 1909, waarin Mondriaan schrijft aan De Iongh dat hij nog geen portretten ontving van Waldenburg. De in deze brief voorgestelde fotosessie heeft dan dus reeds plaatsgevonden. We dateren de onderhavige brief daarom enkele weken eerder dan brief MEP_1293, in de week van maandag 19 tot en met vrijdag 23 april. Brief MEP_1738 zit nog tussen de onderhavige brief en brief MEP_1293 van circa 13 mei in. In die brief wordt meer concreet gestreefd naar een moment om de voorgestelde fotosessie te doen plaatsvinden.\nMet 'de schedelmeter' bedoelt Mondriaan Alfred Waldenburg. De fotosessie met Mondriaan en De Iongh heeft vermoedelijk kort na dit schrijven plaatsgevonden, hoewel de afdrukken pas in augustus gereed waren (zie MEP_1293). Tijdens die sessie maakte Waldenburg tevens een portretfoto van Mondriaan. Hoewel er enkele portretfoto's van De Iongh zijn overgeleverd, is geen daarvan met zekerheid toe te schrijven aan Waldenburg. Correspondentie tussen Mondriaan en Waldenburg is niet overgeleverd. De enige bewaard gebleven tekening van Mondriaan waarvan met zekerheid kan worden gesteld dat De Iongh er model voor stond is de krijttekening [**Meisjeskop**] (UA38). Mogelijk stond zij ook model voor de tekening [**Female nude: bust portrait**] (A645).\n\nDear Zus,\nThe “skull measurer” came round this morning; he saw the sketches of you and thought your forehead and the expression in your eyes so beautiful that he wanted to take a photograph of you. He’s coming here again on Sunday. If you have the time and the inclination, come to me at 10 o’clock, otherwise I’ll just write and tell him you can’t come, in the event that you’d rather decline. It might well take a while before you get a print from him and perhaps he’ll put you in a book later: I’m just warning you, then you can do as you wish.\nLet’s agree that if you don’t have the inclination or the time, drop me a line and otherwise come to me on Sunday morning at 10.\nBye, my dear Zus, your Piet.\nManuscript.\n\n\n\n",
        "metadata": {
          "letter": "19090421y_IONG_1304",
          "folder": "proeftuin"
        }
      },
      "@context": [
        "http://www.w3.org/ns/anno.jsonld",
        {
          "tt": "https://ns.tt.di.huc.knaw.nl/tt",
          "tei": "https://ns.tt.di.huc.knaw.nl/tei"
        }
      ],
      "target": [
        {
          "source": "https://mondriaan.tt.di.huc.knaw.nl/textrepo/rest/versions/9bae62d9-87fb-46e5-8b0b-275554000a35/contents",
          "type": "Text",
          "selector": {
            "type": "tt:TextAnchorSelector",
            "start": 999,
            "end": 1725
          }
        },
        {
          "source": "https://mondriaan.tt.di.huc.knaw.nl/textrepo/view/versions/9bae62d9-87fb-46e5-8b0b-275554000a35/segments/index/999/1725",
          "type": "Text"
        },
        {
          "source": "https://images.diginfra.net/iiif/NL-HaNA_1.01.02%2F3783%2FNL-HaNA_1.01.02_3783_0002.jpg/full/full/0/default.jpg",
          "type": "Image"
        },
        {
          "@context": "https://brambg.github.io/ns/republic.jsonld",
          "source": "https://images.diginfra.net/api/pim/iiif/67533019-4ca0-4b08-b87e-fd5590e7a077/canvas/20633ef4-27af-4b13-9ffe-dfc0f9dad1d7",
          "type": "Canvas"
        }
      ],
      "id": "https://mondriaan.annorepo.dev.clariah.nl/w3c/mondriaan-letters-7/fbc57502-c510-4355-a239-fcbcae5b207d"
    }
  ]
}
```
