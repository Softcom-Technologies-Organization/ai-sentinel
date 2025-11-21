import type { Config } from 'jest';

const config: Config = {
  preset: 'jest-preset-angular',
  testEnvironment: 'jsdom',
  setupFilesAfterEnv: ['<rootDir>/setup-jest.ts'],
  testMatch: ['**/?(*.)+(spec).ts'],
  testPathIgnorePatterns: ['<rootDir>/node_modules/', '<rootDir>/dist/', '<rootDir>/e2e/'],

  // Configuration moderne pour jest-preset-angular v14+
  transform: {
    '^.+\\.(ts|mjs|js|html)$': [
      'jest-preset-angular',
      {
        tsconfig: '<rootDir>/tsconfig.spec.json',
        stringifyContentPathRegex: String.raw`\.(html|svg)$`,
      },
    ],
  },

  // Permettre la transformation des modules ESM d'Angular
  transformIgnorePatterns: [
    String.raw`node_modules/(?!.*\.mjs$|@angular|@jsverse/transloco|@jsverse/utils|primeng|@primeuix)`,
  ],

  moduleFileExtensions: ['ts', 'html', 'js', 'json', 'mjs'],

  // Configuration de la couverture
  collectCoverage: false,
  coverageDirectory: '<rootDir>/coverage/jest',
  coveragePathIgnorePatterns: [
    '/node_modules/',
    '/dist/',
    String.raw`\.spec\.ts$`,
  ],

  reporters: ['default'],

  // Support des modules ESM
  extensionsToTreatAsEsm: ['.ts'],

  // Mapping des modules si nécessaire
  moduleNameMapper: {
    '^@app/(.*)$': '<rootDir>/src/app/$1',
    '^@environments/(.*)$': '<rootDir>/src/environments/$1',
  },
};

export default config;
