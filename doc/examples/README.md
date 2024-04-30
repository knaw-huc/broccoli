# Voorbeelden van gebruik van 🥦 links

## Ophalen data voor bijv. TextAnnoViz

#### Vind de relevante `anno`, `text`, en `iiif` onderdelen voor brief `urn:mondriaan:letter:14569`

* https://broccoli.tt.di.huc.knaw.nl/projects/mondriaan/urn:mondriaan:letter:14569?includeResults=anno,text,iiif

Het antwoord van broccoli is samengesteld:

* `anno` is de betreffende annotatie uit AnnoRepo;
* `text` is het relevante stuk 'segmented text' en komt uit TextRepo;
* `iiif` betreft de scan / het plaatje en komt uit annotaties en gaat over een externe iiif server

(`profile` en `request` in het resultaat kun je negeren: profiling resp. debug info over het request en de uitvoering
ervan)

Dit endpoint kan ook overlappende annotaties vinden.

#### Hiermee vind je bijv. alle `tei:P` paragrafen uit deze brief:

* https://broccoli.tt.di.huc.knaw.nl/projects/mondriaan/urn:mondriaan:letter:14569?includeResults=anno,text,iiif&overlapTypes=tei:P

Het stukje `anno` bevat nu 5 resultaten, waarin de paragraaf annotaties staan.

Ook kun je de locaties van de tekstsegmenten laten herberekenen relatief ten opzichte van de opgevraagde annotatie.

#### Vind de `tei:Rs` elementen in paragraaf `p:14785` en geef hun locaties relatief t.o.v. het omvattende `tei:P` element:

* https://broccoli.tt.di.huc.knaw.nl/projects/mondriaan/urn:mondriaan:p:14785?overlapTypes=tei:Rs&relativeTo=tei:P

Bijvoorbeeld `tei:Rs` element `"de schedelmeter"` met id `urn:mondriaan:rs:15156` is na herberekening terechtgekomen op
regels 3 t/m 6 in het omvattende `tei:P` element.

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

#### Ditto voor (alle) andere types, Textannoviz haalt bijv. nu alle bestaande annotatie types op en laat alle coördinaten van de tekstsegmenten door 🥦 herberekenen t.o.v. de omvattende brief.

* https://broccoli.tt.di.huc.knaw.nl/projects/mondriaan/urn:mondriaan:letter:14569?overlapTypes=tei:Add,tei:Address,tei:Addrline,tei:Altidentifier,tei:Body,tei:C,tei:Change,tei:Choice,tei:Closer,tei:Correspaction,tei:Correspdesc,tei:Country,tei:Date,tei:Dateline,tei:Decodesc,tei:Deconote,tei:Del,tei:Div,tei:Editor,tei:Facsimile,tei:Filedesc,tei:Graphic,tei:Hi,tei:Idno,tei:Institution,tei:Msdesc,tei:Msidentifier,tei:Name,tei:Note,tei:Objectdesc,tei:Opener,tei:Orig,tei:P,tei:Physdesc,tei:Placename,tei:Postmark,tei:Postscript,tei:Profiledesc,tei:Ptr,tei:Publicationstmt,tei:Ref,tei:Reg,tei:Revisiondesc,tei:Rs,tei:Salute,tei:Settlement,tei:Signed,tei:Sourcedesc,tei:Space,tei:Sponsor,tei:Surface,tei:Teiheader,tei:Text,tei:Title,tei:Titlestmt,tei:Unclear,Folder,Letter,Page,Sentence&includeResults=anno,text,iiif&relativeTo=Letter

```
