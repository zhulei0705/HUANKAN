import {defineConfig} from '@playwright/test';
export default defineConfig({testDir:'./e2e-s70',workers:1,timeout:90000,expect:{timeout:20000},use:{baseURL:'http://127.0.0.1:5173',headless:true},reporter:'list'});
