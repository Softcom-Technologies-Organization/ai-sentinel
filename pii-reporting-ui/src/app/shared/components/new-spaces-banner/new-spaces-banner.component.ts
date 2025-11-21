import { Component, ChangeDetectionStrategy, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslocoModule } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';

/**
 * NewSpacesBannerComponent
 *
 * Composant de présentation qui affiche une bannière de notification lorsque de nouveaux espaces
 * Confluence sont disponibles. Ce composant est purement présentationnel et délègue toute logique
 * métier au composant parent via des événements.
 *
 * Règles métier:
 * - La bannière n'est visible que si hasNewSpaces est true
 * - Le nombre d'espaces est affiché dans le message (avec gestion du pluriel via i18n)
 * - Deux actions sont disponibles:
 *   1. Rafraîchir: Recharge la liste des espaces et masque la bannière
 *   2. Fermer: Masque simplement la bannière sans recharger
 *
 * Accessibilité:
 * - Boutons accessibles au clavier (Enter et Space)
 * - Aria-label sur le bouton fermer pour les lecteurs d'écran
 * - Icône visuelle (info-circle) pour indication visuelle
 *
 * @example
 * ```html
 * <app-new-spaces-banner
 *   [hasNewSpaces]="hasNewSpaces()"
 *   [newSpacesCount]="newSpacesCount()"
 *   (refresh)="onRefreshSpaces()"
 *   (dismiss)="onDismissBanner()">
 * </app-new-spaces-banner>
 * ```
 */
@Component({
  selector: 'app-new-spaces-banner',
  standalone: true,
  imports: [CommonModule, TranslocoModule, ButtonModule],
  templateUrl: './new-spaces-banner.component.html',
  styleUrls: ['./new-spaces-banner.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NewSpacesBannerComponent {
  /**
   * Indique si la bannière doit être affichée.
   * Lorsque false, le composant n'est pas rendu dans le DOM.
   */
  @Input() hasNewSpaces: boolean = false;

  /**
   * Nombre de nouveaux espaces Confluence détectés.
   * Cette valeur est affichée dans le message de notification.
   * La traduction gère automatiquement le singulier/pluriel.
   */
  @Input() newSpacesCount: number = 0;

  /**
   * Événement émis lorsque l'utilisateur clique sur le bouton "Rafraîchir".
   * Le composant parent doit:
   * 1. Recharger la liste des espaces depuis le serveur
   * 2. Masquer la bannière (en mettant hasNewSpaces à false)
   */
  @Output() refresh = new EventEmitter<void>();

  /**
   * Événement émis lorsque l'utilisateur clique sur le bouton "Fermer".
   * Le composant parent doit simplement masquer la bannière
   * (en mettant hasNewSpaces à false) sans recharger les données.
   */
  @Output() dismiss = new EventEmitter<void>();

  /**
   * Émet l'événement de rafraîchissement.
   * Appelé lors du clic sur le bouton "Rafraîchir".
   */
  onRefresh(): void {
    this.refresh.emit();
  }

  /**
   * Émet l'événement de fermeture.
   * Appelé lors du clic sur le bouton "Fermer".
   */
  onDismiss(): void {
    this.dismiss.emit();
  }
}
