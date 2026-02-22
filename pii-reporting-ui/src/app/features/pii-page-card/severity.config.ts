import { Severity } from '../../core/models/severity';

export interface SeverityStyle {
  label: string;
  labelKey: string;
  color: string;
  bg: string;
  border: string;
}

export const SEVERITY_STYLES: Record<Severity, SeverityStyle> = {
  high: {
    label: 'Critique',
    labelKey: 'piiItem.severity.high',
    color: '#dc2626',
    bg: '#fef2f2',
    border: '#fecaca',
  },
  medium: {
    label: 'Moyenne',
    labelKey: 'piiItem.severity.medium',
    color: '#ea580c',
    bg: '#fff7ed',
    border: '#fed7aa',
  },
  low: {
    label: 'Faible',
    labelKey: 'piiItem.severity.low',
    color: '#16a34a',
    bg: '#f0fdf4',
    border: '#bbf7d0',
  },
};
