import {defineConfig} from '@playwright/test';
export default defineConfig({testDir:'./e2e-s66',workers:1,timeout:90000,expect:{timeout:20000},use:{baseURL:'http://127.0.0.1:5180',headless:true},reporter:'list'});
