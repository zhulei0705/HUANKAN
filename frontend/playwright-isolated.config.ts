import {defineConfig} from '@playwright/test';
export default defineConfig({testDir:'./e2e-isolated',workers:1,use:{baseURL:'http://127.0.0.1:5175',headless:true},webServer:{command:'npm.cmd run dev -- --port 5175 --strictPort',url:'http://127.0.0.1:5175',reuseExistingServer:false,env:{HUANKAN_BACKEND_URL:'http://127.0.0.1:18081'}},reporter:'list'});
