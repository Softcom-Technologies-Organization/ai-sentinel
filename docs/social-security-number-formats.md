# Formats internationaux et post-filtrage des numéros de sécurité sociale

Recherche du 15 septembre 2026. Longueurs comptées sans espaces ni séparateurs de présentation ; les lettres comptent comme des caractères.

## Conclusion

**8 caractères est un seuil pratique pour les formats courants de l'UE étudiés, mais pas un minimum européen ou mondial.** Les sources officielles donnent des contre-exemples : CASS andorrane à 7 caractères, matricule monégasque à 5 ou 6 chiffres, AVS du Liechtenstein à partir de 4 chiffres. La fiche de Saint-Marin indique même « plus d'un chiffre » et autorise l'omission des zéros initiaux.

La règle précédente « au moins 9 chiffres » exclut notamment le NINO britannique, le PPS irlandais, le codice fiscale italien, le numéro maltais et les petits États européens. Compter les caractères alphanumériques corrige le problème des lettres ; cela ne résout pas les différences de longueur.

Il faut distinguer :

- le numéro social proprement dit ;
- un identifiant national réutilisé dans les démarches sociales ;
- les identifiants fiscaux ou de santé, indiqués séparément quand la source les documente. Un TIN n'est pas automatiquement un numéro social.

Les sources Microsoft décrivent leurs détecteurs, pas nécessairement tous les formats légaux. Les documents des organismes émetteurs priment. Les regex ci-dessous décrivent la forme ; elles ne prouvent ni une attribution réelle, ni une date ou une clé de contrôle correcte.

## Union européenne : 27 pays

| Pays | Identifiant étudié | Caractères hors séparateurs | Source |
|---|---|---:|---|
| Allemagne | Rentenversicherungsnummer | 12 : 8 chiffres, 1 lettre, 3 chiffres | [Deutsche Rentenversicherung](https://rvrecht.deutsche-rentenversicherung.de/SharedDocs/rvRecht/05_Normen_und_Vertraege/02_S-Z/VKVV/0002/0002_2020_07_01.html) |
| Autriche | Versicherungsnummer | 10 chiffres | [Microsoft](https://learn.microsoft.com/en-us/purview/sit-defn-austria-social-security-number) |
| Belgique | NISS / numéro national | 11 chiffres | [Microsoft](https://learn.microsoft.com/en-us/purview/sit-defn-belgium-national-number) |
| Bulgarie | EGN | 10 chiffres | [Microsoft](https://learn.microsoft.com/en-us/purview/sit-defn-bulgaria-uniform-civil-number) |
| Chypre | Social Insurance Number | 8 chiffres dans le fichier officiel | [Services des assurances sociales](https://sisweb.mlsi.gov.cy/icms-cont/uploads/2021/02/file-upload-eng.pdf) |
| Croatie | OIB, identifiant personnel | 11 chiffres | [Microsoft](https://learn.microsoft.com/en-us/purview/sit-defn-croatia-personal-identification-number) |
| Danemark | CPR | 10 chiffres | [Microsoft](https://learn.microsoft.com/en-us/purview/sit-defn-denmark-personal-identification-number) |
| Espagne | NUSS / NAF | 11–12 chiffres dans le détecteur | [Microsoft](https://learn.microsoft.com/en-us/purview/sit-defn-spain-social-security-number) |
| Estonie | Isikukood | 11 chiffres | [Microsoft](https://learn.microsoft.com/en-us/purview/sit-defn-estonia-personal-identification-code) |
| Finlande | HETU | 11 au format original ; 10 si le marqueur de siècle est `+` ou `-` et supprimé | [Microsoft](https://learn.microsoft.com/en-us/purview/sit-defn-finland-national-id), [validateur](https://github.com/arthurdejong/python-stdnum/blob/master/stdnum/fi/hetu.py) |
| France | NIR | 13, ou 15 avec clé ; lettres possibles pour la Corse | [Service Public](https://www.service-public.gouv.fr/particuliers/vosdroits/F33078), [validateur](https://github.com/arthurdejong/python-stdnum/blob/master/stdnum/fr/nir.py) |
| Grèce | AMKA | 11 chiffres | [Microsoft](https://learn.microsoft.com/en-us/purview/sit-defn-greece-social-security-number) |
| Hongrie | TAJ | 9 chiffres | [Microsoft](https://learn.microsoft.com/en-us/purview/sit-defn-hungary-social-security-number) |
| Irlande | PPS | 8 ou 9 : 7 chiffres et 1 ou 2 lettres | [Microsoft](https://learn.microsoft.com/en-us/purview/sit-defn-ireland-personal-public-service-number) |
| Italie | Codice fiscale personnel | 16 alphanumériques ; chiffres remplaçables par des lettres en cas d'omocodia | [Validateur python-stdnum](https://github.com/arthurdejong/python-stdnum/blob/master/stdnum/it/codicefiscale.py) |
| Lettonie | Personas kods | 11 chiffres | [Microsoft](https://learn.microsoft.com/en-us/purview/sit-defn-latvia-personal-code) |
| Lituanie | Asmens kodas | 11 chiffres | [Microsoft](https://learn.microsoft.com/en-us/purview/sit-defn-lithuania-personal-code) |
| Luxembourg | Matricule national | 13 chiffres | [Microsoft](https://learn.microsoft.com/en-us/purview/sit-defn-luxemburg-national-identification-number-natural-persons) |
| Malte | Carte d'identité utilisée comme numéro social pour les séries concernées | 8 : 7 chiffres et 1 lettre | [Sécurité sociale](https://socialsecurity.gov.mt/en/social-security-number/), [Identità](https://identita.gov.mt/identity-cards-unit/useful-info/) |
| Pays-Bas | BSN | 8 ou 9 dans le détecteur, notamment avec zéro initial omis | [Microsoft](https://learn.microsoft.com/en-us/purview/sit-defn-netherlands-citizens-service-number), [validateur](https://github.com/arthurdejong/python-stdnum/blob/master/stdnum/nl/bsn.py) |
| Pologne | PESEL | 11 chiffres | [Microsoft](https://learn.microsoft.com/en-us/purview/sit-defn-poland-national-id) |
| Portugal | NISS | 11 chiffres ; anciens numéros à 9 | [Gouvernement](https://www.gov.pt/guias/seguranca-social-direitos-e-obrigacoes-dos-empregadores-em-portugal), [DGES](https://dges.gov.pt/en/node/266) |
| Roumanie | CNP | 13 chiffres | [Microsoft](https://learn.microsoft.com/en-us/purview/sit-defn-romania-personal-numeric-code) |
| Slovaquie | Rodné číslo | 9 ou 10 chiffres | [Microsoft](https://learn.microsoft.com/en-us/purview/sit-defn-slovakia-personal-number) |
| Slovénie | EMŠO | 13 chiffres | [Microsoft](https://learn.microsoft.com/en-us/purview/sit-defn-slovenia-unique-master-citizen-number) |
| Suède | Personnummer | 10 ou 12 chiffres | [Microsoft](https://learn.microsoft.com/en-us/purview/sit-defn-sweden-national-id) |
| Tchéquie | Rodné číslo | 9 ou 10 chiffres | [Microsoft](https://learn.microsoft.com/en-us/purview/sit-defn-czech-personal-identity-number) |

## Autres pays européens et région proche

| Pays / territoire | Identifiant étudié | Caractères hors séparateurs | Source |
|---|---|---:|---|
| Royaume-Uni | NINO | 9 : 2 lettres, 6 chiffres, 1 lettre | [HMRC](https://www.gov.uk/hmrc-internal-manuals/national-insurance-manual/nim39110) |
| Suisse | AVS actuel | 13 chiffres ; anciens formats distincts à préserver dans les archives | [Centrale de compensation](https://www.zas.admin.ch/de/ahv-nummer-identifikator) |
| Norvège | Fødselsnummer | 11 chiffres | [UDI](https://www.udi.no/en/word-definitions/fodselsnummer/) |
| Islande | Kennitala | 10 chiffres | [Fiche nationale OCDE](https://www.oecd.org/content/dam/oecd/en/topics/policy-issue-focus/aeoi/iceland-tin.pdf) |
| Liechtenstein | AVS | **4–10 chiffres** dans la notice d'introduction ; le PEID fiscal distinct est documenté jusqu'à 12 | [AHV](https://www.ahv.li/aktuelles/newsletter/archiv-bis-2010/detail/information-ueber-die-abloesung-der-heute-gueltigen-ahv-nummer), [PEID](https://www.oecd.org/content/dam/oecd/en/topics/policy-issue-focus/aeoi/liechtenstein-tin.pdf) |
| Andorre | CASS | **7 : 6 chiffres et 1 lettre** | [Formulaire CASS](https://www.cass.ad/contacte) |
| Monaco | Matricule des Caisses sociales | **5 ou 6 chiffres** | [Caisses sociales](https://www.en.caisses-sociales.mc/data/legal-notice-and-terms-and-conditions-of-use-insured-personal-space-in-french) |
| Saint-Marin | Codice ISS / SSI Number | **Variable : plus d'un chiffre**, zéros initiaux omissibles ; pas de minimum 8 justifiable | [Fiche nationale OCDE](https://www.oecd.org/content/dam/oecd/en/topics/policy-issue-focus/aeoi/san-marino-tin.pdf) |
| Albanie | NID, également utilisé pour la sécurité sociale | 10 alphanumériques | [Fiche nationale OCDE](https://www.oecd.org/content/dam/oecd/en/topics/policy-issue-focus/aeoi/albania-tin.pdf) |
| Serbie | LBO, numéro personnel d'assuré | 11 chiffres | [Assurance maladie RFZO](https://rfzo.gov.rs/index.php/osiguranalica/provera-overe-zdrisp) |
| Bosnie-Herzégovine, Monténégro | JMB / JMBG, identifiant personnel | 13 chiffres dans l'implémentation ; aucune exhaustivité revendiquée pour les numéros propres aux caisses | [Implémentation JMBG](https://www.npmjs.com/package/%40jmbg-labs/jmbg) |
| Macédoine du Nord | EMBG | 13 chiffres | [Loi, traduction publiée par le HCR](https://www.refworld.org/sites/default/files/2025-02/28_10_2024_law_on_personal_identification_number_north_macedonia_eng.pdf) |
| Kosovo | Numéro personnel | 10 chiffres | [Instruction administrative](https://gzk.rks-gov.net/ActDocumentDetail.aspx?ActID=12653) |
| Moldavie | IDNP | 13 chiffres | [Fiche nationale OCDE](https://www.oecd.org/content/dam/oecd/en/topics/policy-issue-focus/aeoi/moldova-tin.pdf) |
| Ukraine | RNOKPP, identifiant fiscal personnel | 10 chiffres ; ne pas confondre avec le code des entreprises | [Fiche nationale OCDE](https://www.oecd.org/content/dam/oecd/en/topics/policy-issue-focus/aeoi/ukraine-tin.pdf) |
| Russie | SNILS | 11 chiffres | [Fonds social russe](https://sfr.gov.ru/branches/karelia/news~2026/04/13/280341) |
| Turquie | T.C. Kimlik | 11 chiffres | [Microsoft](https://learn.microsoft.com/en-us/purview/sit-defn-turkey-national-identification-number) |
| Jersey | Numéro social | 9 : 2 lettres, 6 chiffres, 1 lettre | [Gouvernement de Jersey](https://www.gov.je/TaxesMoney/InternationalTaxAgreements/IGAs/Pages/CommonReportingStandard.aspx/1000) |
| Arménie | Public Services Number | 10 chiffres ; distinct du TIN à 8 chiffres | [Rapport du service danois de l'immigration](https://us.dk/media/af4aasxy/armenia_ffm_2016.pdf) |
| Azerbaïdjan | PIN personnel, identifiant connexe | **7 alphanumériques** ; le numéro social proprement dit n'est pas confirmé ici | [Fiche nationale OCDE](https://www.oecd.org/content/dam/oecd/en/topics/policy-issue-focus/aeoi/azerbaijan-tin.pdf) |
| Géorgie | Identifiant personnel des citoyens | 11 chiffres ; autre identifiant fiscal à 9 chiffres pour les étrangers | [Fiche nationale OCDE](https://www.oecd.org/content/dam/oecd/en/topics/policy-issue-focus/aeoi/georgia-tin.pdf) |
| Kazakhstan | IIN personnel | 12 chiffres | [Fiche nationale OCDE](https://www.oecd.org/content/dam/oecd/en/topics/policy-issue-focus/aeoi/kazakhstan-tin.pdf) |

Limites européennes : cette recherche n'établit pas le format exhaustif des identifiants sociaux de Biélorussie et du Vatican, ni de tous les numéros historiques, territoriaux ou propres à une caisse. Ces pays ne sont pas implicitement considérés comme compatibles avec un seuil de 8.

## Principaux pays hors Europe

| Pays / territoire | Identifiant étudié | Caractères hors séparateurs | Source |
|---|---|---:|---|
| États-Unis | SSN | 9 chiffres | [Microsoft](https://learn.microsoft.com/en-us/purview/sit-defn-us-social-security-number) |
| Canada | NAS / SIN | 9 chiffres | [Microsoft](https://learn.microsoft.com/en-us/purview/sit-defn-canada-social-insurance-number) |
| Mexique | NSS | 11 chiffres | [IMSS](https://www.imss.gob.mx/node/73993) |
| Brésil | CPF, identifiant fiscal personnel | 11 chiffres | [Microsoft](https://learn.microsoft.com/en-us/purview/sit-defn-brazil-cpf-number) |
| Argentine | CUIL | 11 chiffres | [Microsoft](https://learn.microsoft.com/en-us/purview/sit-defn-argentina-unique-tax-identification-key) |
| Australie | Centrelink CRN ; Medicare ; TFN fiscal | 10 alphanumériques ; 10–11 chiffres ; 8–9 chiffres | [Centrelink](https://www.centrelink.gov.au/wps/PA_1F-updateNominee/help/help.jsp), [Medicare](https://learn.microsoft.com/en-us/purview/sit-defn-australia-medical-account-number), [TFN](https://learn.microsoft.com/en-us/purview/sit-defn-australia-tax-file-number) |
| Nouvelle-Zélande | Social welfare ; NHI de santé | 9 chiffres ; **7** pour le NHI historique (3 lettres et 4 chiffres) | [Social welfare](https://learn.microsoft.com/en-us/purview/sit-defn-new-zealand-social-welfare-number), [NHI](https://learn.microsoft.com/en-us/purview/sit-defn-new-zealand-ministry-of-health-number) |
| Chine | Numéro d'identité du résident | 18 ; dernière position potentiellement `X` | [Microsoft](https://learn.microsoft.com/en-us/purview/sit-defn-china-resident-identity-card-number), [validateur](https://github.com/arthurdejong/python-stdnum/blob/master/stdnum/cn/ric.py) |
| Hong Kong | HKID | 8–9 alphanumériques | [Microsoft](https://learn.microsoft.com/en-us/purview/sit-defn-hong-kong-identity-card-number) |
| Inde | Aadhaar, identifiant personnel | 12 chiffres | [Microsoft](https://learn.microsoft.com/en-us/purview/sit-defn-india-unique-identification-number) |
| Japon | Basic Pension Number ; My Number | 10 chiffres ; 12 chiffres | [Japan Pension Service](https://www.nenkin.go.jp/service/pamphlet/kaigai/kankeitodoke.files/00.pdf) |
| Japon, détecteur large | Social insurance number dans Microsoft | **7–12 chiffres**, plage du détecteur et non format unique officiel | [Microsoft](https://learn.microsoft.com/en-us/purview/sit-defn-japan-social-insurance-number) |
| Corée du Sud | RRN | 13 chiffres | [Microsoft](https://learn.microsoft.com/en-us/purview/sit-defn-south-korea-resident-registration-number) |
| Singapour | NRIC / FIN ; UIN utilisé comme compte CPF | 9 : 1 lettre, 7 chiffres, 1 lettre | [Microsoft](https://learn.microsoft.com/en-us/purview/sit-defn-singapore-national-registration-identity-card-number), [CPF](https://ask.gov.sg/cpf/questions/cm2s5b54d006b12kz0mefc4pt) |
| Taïwan | Identifiant national | 10 : 1 lettre et 9 chiffres | [Microsoft](https://learn.microsoft.com/en-us/purview/sit-defn-taiwan-national-identification-number) |
| Afrique du Sud | Identifiant national | 13 chiffres | [Microsoft](https://learn.microsoft.com/en-us/purview/sit-defn-south-africa-identification-number) |
| Arabie saoudite | Identifiant national | 10 chiffres | [Microsoft](https://learn.microsoft.com/en-us/purview/sit-defn-saudi-arabia-national-id) |
| Émirats arabes unis | Emirates ID | 15 chiffres | [Microsoft](https://learn.microsoft.com/en-us/purview/sit-defn-uae-identity-card-number) |

## Regex et validateurs réutilisables

[python-stdnum](https://github.com/arthurdejong/python-stdnum) est déjà une dépendance du service. Ses modules fournissent la normalisation, les contrôles de format et souvent la clé : `ch.ssn`, `at.vnr`, `be.nn`, `fr.nir`, `ie.pps`, `it.codicefiscale`, `nl.bsn`, `us.ssn`, `ca.sin`, `fi.hetu`, `dk.cpr`, `se.personnummer`, etc. Ce n'est pas un validateur universel de tous les numéros sociaux ; plusieurs pays et plusieurs types manquent.

Le [catalogue Microsoft Purview](https://learn.microsoft.com/en-us/purview/sit-sensitive-information-type-entity-definitions) donne les motifs, les séparateurs, les mots de contexte et les contrôles par type. Certaines règles exposent le nom d'une fonction interne plutôt qu'une regex directement copiable.

Exemples de **regex de forme simplifiées, déduites des sources**, à appliquer avec `fullmatch` sur une valeur normalisée en majuscules. Elles ne remplacent pas les validateurs nationaux :

| Format | Regex de forme |
|---|---|
| SSN US / NAS canadien | `[0-9]{9}` |
| AVS suisse actuel | `756[0-9]{10}` |
| NINO britannique | `[A-Z]{2}[0-9]{6}[A-D]` ; restrictions supplémentaires sur les préfixes |
| PPS irlandais | `[0-9]{7}[A-Z]{1,2}` |
| Allemagne RVNR | `[0-9]{8}[A-Z][0-9]{3}` |
| Malte, identité | `[0-9]{7}[ABGHLMPZ]` ; l'utilisation comme numéro social dépend de la série |
| CASS Andorre | `[0-9]{6}[A-Z]` |
| Monaco | `[0-9]{5,6}` |
| Liechtenstein, notice AVS | `[0-9]{4,10}` |
| Italie avec omocodia | `[A-Z]{6}[0-9LMNPQRSTUV]{2}[ABCDEHLMPRST][0-9LMNPQRSTUV]{2}[A-Z][0-9LMNPQRSTUV]{3}[A-Z]` |

Supprimer les séparateurs uniquement pour compter la longueur. Ne pas jeter le texte original : `+` et `-` ont un sens dans certains formats nordiques. Ne pas supprimer les lettres : elles font partie du numéro. Ne pas compter le contexte de la phrase comme une partie de l'identifiant.

## Règle déduite pour le service

Deux objectifs ne peuvent pas être garantis ensemble par une simple longueur : rejeter tous les `254` et accepter sans contexte tous les formats sociaux européens, puisque Saint-Marin documente des identifiants numériques courts.

La règle générale recommandée est donc :

1. Reconnaître un **identifiant candidat**, en séparant sa valeur de la phrase qui l'entoure.
2. **Seuil courant : 8 caractères alphanumériques**, lettres incluses, séparateurs exclus. Ce seuil est un choix de filtrage pour les formats courants étudiés, pas une propriété universelle.
3. **Pour les formats courts documentés**, n'appliquer une longueur nationale que si le pays ou l'organisme est identifié par une information fiable. Exemples : CASS Andorre, Caisses sociales Monaco, AVS Liechtenstein, ISS Saint-Marin. Un simple nombre court ne permet pas de déterminer son pays.
4. **Sans cette information, un rejet sous 8 implique un compromis explicite** : ces numéros courts peuvent être perdus. À l'inverse, les préserver sans contexte laisse passer certains numéros de tickets.
5. Ne pas imposer un minimum mondial de chiffres : les formats alphanumériques, notamment le codice fiscale avec omocodia, l'invalident aussi.

Pour la capture, `254` et `CEI 257` sont sous 8 caractères utiles ; `Tests PEE 271 incomplete` exige aussi de contrôler la forme ou le contexte. Allonger le texte en ajoutant des mots ne transforme pas le ticket `271` en numéro social.

Cette recherche ne modifie pas le code : elle remplace la justification erronée d'un seuil supposé universel par un périmètre et des exceptions documentés. Le seuil de 9 chiffres présent dans la stratégie Python reste à corriger selon la politique retenue pour les pays courts.
