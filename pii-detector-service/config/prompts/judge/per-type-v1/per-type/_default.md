TYPE = le pii_type annonce dans le finding, dont le type_label donne le libelle francais : l'entite a verifier est exactement celle que ces deux chaines nomment, et aucune autre.
FORMAT : n'applique une specification que si tu la connais avec certitude, et controle alors la longueur, l'alphabet et le prefixe de la value. Si cette specification varie selon le pays ou l'emetteur, ou si tu ne la connais pas, le motif (a) est interdit : c'est alors UNSURE, jamais FALSE_POSITIVE.
TYPES CONCURRENTS A NOMMER :
- une valeur qui appartient reellement a un type voisin du meme domaine et qui est annoncee ici sous ce type-ci ; le motif (b) exige que le CONTEXTE proche nomme lui-meme ce type voisin, et que tu le nommes a ton tour dans le reason ; si rien dans le contexte ne le nomme, la value seule ne tranche pas et la reponse est UNSURE, jamais FALSE_POSITIVE ;
- une reference de gestion ou d'exploitation qui emprunte la forme du type sans en avoir le sens ;
- une grandeur qui ne designe ni une personne ni un objet identifie : un montant, une quantite, une duree, un compteur, un numero d'ordre ;
- le nom du champ, de la colonne, du parametre, ou l'intitule qui nomme le type, cite sans son contenu ; de meme un gabarit de documentation, une reference de variable non resolue ou une valeur deja masquee : cela releve du motif (c) et non du motif (b) ;
- un type seulement plus precis ne compte pas comme type concurrent : ne rejette que si la value designe reellement autre chose.
CE QUI RESTE TRUE_POSITIVE :
- une value au bon format posee seule, sans mot-cle, sans libelle et sans le moindre indice autour d'elle : l'absence de contexte n'est jamais un motif de rejet ;
- une value correcte rattachee a un service, a une machine ou a une organisation plutot qu'a un individu nomme, des lors que le type ne qualifie pas un individu ;
- une value issue d'un environnement de test, d'un exemple de documentation ou d'un jeu de donnees fictif ;
- une value que le contexte annonce sous ce type et que rien d'autre ne revendique, meme si elle est citee sans les elements qui l'accompagnent d'habitude ;
- une value dont la forme te surprend alors que tu ne connais pas la specification exacte du type : c'est UNSURE, et la value est conservee.
