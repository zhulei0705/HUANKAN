import {defineConfig} from '@playwright/test';
export default defineConfig({testDir:'./e2e',workers:1,timeout:60000,expect:{timeout:15000},use:{baseURL:'http://127.0.0.1:5173',headless:true},webServer:{command:'npm.cmd run dev -- --port 5173 --strictPort',url:'http://127.0.0.1:5173',reuseExistingServer:true},reporter:'list'});
