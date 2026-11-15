import { Routes } from '@angular/router';
import { Dashboard } from './components/dashboard/dashboard';
import { Products } from './components/products/products';
import { Batches } from './components/batches/batches';
import { Odoo } from './components/odoo/odoo';
import { PurchaseOrders } from './components/purchase-orders/purchase-orders';

export const routes: Routes = [
    { path: '',                redirectTo: 'dashboard', pathMatch: 'full' },
    { path: 'dashboard',       component: Dashboard },
    { path: 'products',        component: Products },
    { path: 'batches/:productId', component: Batches },
    { path: 'odoo',            component: Odoo },
    { path: 'purchase-orders', component: PurchaseOrders },
    { path: '**',              redirectTo: 'dashboard' }
];