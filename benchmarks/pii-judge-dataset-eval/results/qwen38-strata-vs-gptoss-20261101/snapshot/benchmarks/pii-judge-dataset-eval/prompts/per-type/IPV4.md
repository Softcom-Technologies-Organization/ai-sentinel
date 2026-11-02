TYPE = IPV4 (Adresse IPv4) : l'adresse d'un hote sur un reseau IP en version quatre, a l'exclusion de la version six.
FORMAT : exactement quatre nombres decimaux de 0 a 255 separes par trois points, sans autre caractere. Specification internationale stable : moins de quatre groupes, plus de quatre groupes, un nombre au-dela de 255 ou la presence d'une lettre violent le format.
TYPES CONCURRENTS A NOMMER : ne rejette au titre du type concurrent que si le contexte NOMME le type rival dans les soixante caracteres autour de la value ; si rien ne le nomme, la reponse est UNSURE, jamais FALSE_POSITIVE.
- une adresse IPv6 : des groupes hexadecimaux separes par des deux-points relevent du type IPV6, pas de celui-ci, meme s'ils designent bien un hote ;
- un couple de coordonnees geographiques, latitude et longitude : une partie decimale, parfois un signe, ce qu'aucun octet d'adresse ne porte ;
- deux ou trois nombres pointes seulement, qui ne sont pas une adresse : une date ecrite avec des points, un numero de version, un numero de norme ou de chapitre ;
- une heure ecrite avec des deux-points, un port reseau seul, un code postal ou un nombre isole ;
- un masque de sous-reseau ou une notation CIDR, des lors que le contexte les presente comme une plage et non comme un hote ;
- la locution qui nomme le type, en francais ou en anglais (adresse IPv4, adresse IP, IPv4, IP address) : c'est le nom du champ, pas son contenu ;
- une valeur deja masquee ou un gabarit de documentation, entre crochets, entre accolades, en etoiles ou en X.
CE QUI RESTE TRUE_POSITIVE : une adresse privee, de bouclage, de documentation, de multidiffusion ou d'un reseau interne ; l'adresse d'un serveur, d'une passerelle, d'une imprimante ou d'un equipement, sans aucune personne associee ; une adresse suivie de deux-points et d'un port, des lors que le span couvre bien les quatre groupes ; une adresse nue, sans mot-cle ni libelle autour : quatre groupes valides separes par des points suffisent a conclure TRUE_POSITIVE.
