import {Component, inject, signal} from '@angular/core';
import {AuthService} from '../../core/auth/auth.service';
import {CommonModule} from '@angular/common';
import {ButtonModule} from 'primeng/button';
import {ToolbarModule} from 'primeng/toolbar';
import {AvatarModule} from 'primeng/avatar';
import {MenuModule} from 'primeng/menu';
import {MenuItem} from 'primeng/api';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, ButtonModule, ToolbarModule, AvatarModule, MenuModule],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss']
})
export class DashboardComponent {
  private authService = inject(AuthService);

  userName = signal(this.authService.profile()?.['name'] || 'User');

  menuItems: MenuItem[] = [
    {
      label: 'Profil',
      icon: 'pi pi-user',
    },
    {
      separator: true
    },
    {
      label: 'Logout',
      icon: 'pi pi-sign-out',
      command: () => this.authService.logout()
    }
  ]
}
