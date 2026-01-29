import { Routes } from '@angular/router';
import { DashboardComponent } from './dashboard.component';


export const dashboardRoutes: Routes = [
  {
    path: '', // Das entspricht '/dashboard' (wurde in app.routes schon definiert)
    component: DashboardComponent, // Lade erst den Rahmen (Layout)

    // 👇 HIER kommen die Unterseiten rein, die IM Rahmen angezeigt werden
    children: [
      {
        path: '',
        redirectTo: 'members',
        pathMatch: 'full'
      },
      // Hier könnten später weitere kommen:
      // { path: 'settings', component: SettingsComponent }
    ]
  }
];
