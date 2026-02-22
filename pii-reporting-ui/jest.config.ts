import type { Config } from 'jest';

const config: Config = {
  preset: 'jest-preset-angular',
  testEnvironment: 'jsdom',
  setupFilesAfterEnv: ['<rootDir>/setup-jest.ts'],
  testMatch: ['**/?(*.)+(spec).ts'],
  testPathIgnorePatterns: ['<rootDir>/node_modules/', '<rootDir>/dist/', '<rootDir>/e2e/'],

  // Permet de transformer certains modules ESM dans node_modules (Angular, Transloco, utils, PrimeNG)
  transformIgnorePatterns: [
    String.raw`node_modules/(?!.*\.mjs$|@angular|@jsverse/transloco|@jsverse/utils|primeng|@primeuix)`,
  ],

  // Configuration de la couverture
  collectCoverage: false,
  coverageDirectory: '<rootDir>/coverage/jest',
  coveragePathIgnorePatterns: ['/node_modules/', '/dist/', String.raw`\.spec\.ts$`],

  reporters: ['default'],

  // Mapping des modules si nécessaire
  moduleNameMapper: {
    '^@app/(.*)$': '<rootDir>/src/app/$1',
    '^@environments/(.*)$': '<rootDir>/src/environments/$1',
    '^primeng/multiselect$': '<rootDir>/node_modules/primeng/fesm2022/primeng-multiselect.mjs',
    '^primeng/types/multiselect$': '<rootDir>/node_modules/primeng/fesm2022/primeng-types-multiselect.mjs',
  },
};

export default config;
