TYPE = IPV6 (Adresse IPv6) : l'adresse d'un hote sur un reseau IP en version six, a l'exclusion de la version quatre.
FORMAT : huit groupes de un a quatre chiffres hexadecimaux separes par des deux-points, les zeros de tete pouvant etre omis et une seule suite de groupes nuls pouvant etre abregee par un double deux-points. Specification internationale stable : une valeur sans aucun deux-points, ou dont les groupes sortent de l'alphabet hexadecimal, viole le format.
TYPES CONCURRENTS A NOMMER : ne rejette au titre du type concurrent que si le contexte NOMME le type rival dans les soixante caracteres autour de la value ; si rien ne le nomme, la reponse est UNSURE, jamais FALSE_POSITIVE.
- une adresse IPv4 : quatre nombres decimaux separes par des points relevent du type IPV4, pas de celui-ci, meme s'ils designent bien un hote ;
- une heure ecrite avec des deux-points, seule ou dans un horodatage ;
- un couple hote et port, ou un port reseau cite seul derriere des deux-points ;
- un couple de coordonnees geographiques, latitude et longitude, reconnaissable a sa partie decimale, parfois a un signe, ce qu'aucun groupe d'adresse ne porte ;
- un identifiant purement hexadecimal d'un autre type : adresse MAC en six groupes de deux, empreinte, hash, numero de serie ;
- un prefixe de routage ou une notation CIDR, des lors que le contexte les presente comme une plage et non comme un hote ;
- la locution qui nomme le type, en francais ou en anglais (adresse IPv6, adresse IP, IPv6, IP address) : c'est le nom du champ, pas son contenu ;
- une valeur deja masquee ou un gabarit de documentation, entre accolades, en etoiles, en X, ou entre crochets qui entourent un intitule ou un mot de gabarit ; les crochets d'un litteral d'hote IPv6 ne valent jamais ce motif.
CE QUI RESTE TRUE_POSITIVE : une adresse de bouclage, de lien local, unique locale, de documentation ou de multidiffusion ; une forme fortement abregee par le double deux-points, y compris tres courte ; une forme mixte dont les deux derniers groupes s'ecrivent en quatre nombres pointes ; une adresse entre crochets suivie d'un port, ou suivie d'un identifiant de zone apres un pourcent ; l'adresse d'un serveur ou d'un equipement sans personne associee ; une adresse nue, sans mot-cle ni libelle autour, qui reste TRUE_POSITIVE par le seul respect de la specification.
