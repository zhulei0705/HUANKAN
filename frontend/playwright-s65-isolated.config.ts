import {defineConfig} from '@playwright/test';
export default defineConfig({testDir:'./e2e-s65-isolated',workers:1,timeout:90000,use:{baseURL:'http://127.0.0.1:5177',headless:true},webServer:{command:'npm.cmd run dev -- --port 5177 --strictPort',url:'http://127.0.0.1:5177',reuseExistingServer:false,env:{HUANKAN_BACKEND_URL:'http://127.0.0.1:18084'}},reporter:'list'});


