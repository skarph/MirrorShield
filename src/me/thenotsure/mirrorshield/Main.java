package me.thenotsure.mirrorshield;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Banner;
import org.bukkit.block.banner.Pattern;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ShulkerBullet;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import net.md_5.bungee.api.ChatColor;

public class Main extends JavaPlugin implements Listener{
	//You can edit these
	private JavaPlugin plugin;
	
	
	private boolean DEBUG_MODE;
	
	private int SHIELD_PARTICLE_COUNT;
	
	private double PROJECTILE_SPEED_MULTIPLYER;
	private double ATTACKER_SPEED_MULTIPLYER;
	private double PROJECTILE_INTERPOLATION;
	private double ATTACKER_INTERPOLATION;
	private double ENTITY_DETECTION_RADIUS;
	private double MINIMUM_REFLECT_SPEED;
	private double SHIELD_PARTICLE_RADIUS;
	private double SHIELD_PARTICLE_SPEED;
	private double SHIELD_VOLUME;
	
	private Particle SHIELD_PARTICLE_TYPE;
	private Sound SHIELD_NOISE;
	private DyeColor MIRROR_SHIELD_BANNER_BASE;
	private List<Pattern> MIRROR_SHIELD_BANNER_LAYOUT;	
	
	private String UNCHARGED_MIRROR_SHIELD_DISPLAY_NAME;
	private List<String> UNCHARGED_MIRROR_SHIELD_LORE;
	private String MIRROR_SHIELD_DISPLAY_NAME;
	private List<String> MIRROR_SHIELD_LORE;
	
	
	//No touch from here on out, mkay?
	private ItemStack mirrorShieldItem = new ItemStack(Material.SHIELD, 1);
	private ItemStack mirrorShieldUnchargedItem = new ItemStack(Material.SHIELD, 1);
	private String UNCHARGED_MIRROR_SHIELD_CRAFTING_TITLE = "mirror_shield_uncharged";
	private final List<Class <? extends Projectile>> SHOOTER_REASSIGNMENT_BLACKLIST = Arrays.asList(
			EnderPearl.class,
			FishHook.class,
			Trident.class
			);
	//Code start!
	
	@SuppressWarnings("unchecked")
	@Override
	public void onEnable() {
		Bukkit.getPluginManager().registerEvents(this,this);
		
		FileConfiguration config = plugin.getConfig();
		
		DEBUG_MODE = config.getBoolean("DEBUG_MODE");
		
		SHIELD_PARTICLE_COUNT = config.getInt("SHIELD_PARTICLE_COUNT");
		
		PROJECTILE_SPEED_MULTIPLYER = config.getDouble("PROJECTILE_SPEED_MULTIPLYER");
		ATTACKER_SPEED_MULTIPLYER = config.getDouble("ATTACKER_SPEED_MULTIPLYER");
		PROJECTILE_INTERPOLATION = config.getDouble("PROJECTILE_INTERPOLATION");
		ATTACKER_INTERPOLATION = config.getDouble("ATTACKER_INTERPOLATION");
		ENTITY_DETECTION_RADIUS = config.getDouble("ENTITY_DETECTION_RADIUS");
		MINIMUM_REFLECT_SPEED = config.getDouble("MINIMUM_REFLECT_SPEED");
		SHIELD_PARTICLE_RADIUS = config.getDouble("SHIELD_PARTICLE_RADIUS");
		SHIELD_PARTICLE_SPEED = config.getDouble("SHIELD_PARTICLE_SPEED");
		SHIELD_VOLUME = config.getDouble("SHIELD_VOLUME");
		
		SHIELD_NOISE = (Sound) Sound.valueOf(config.getString("SHIELD_NOISE"));
		SHIELD_PARTICLE_TYPE = (Particle) Particle.valueOf(config.getString("SHIELD_PARTICLE_TYPE"));
		MIRROR_SHIELD_BANNER_BASE = (DyeColor) DyeColor.valueOf(config.getString("MIRROR_SHIELD_BANNER_BASE"));
		MIRROR_SHIELD_BANNER_LAYOUT = (List<Pattern>) config.getList("MIRROR_SHIELD_BANNER_LAYOUT");
		
		UNCHARGED_MIRROR_SHIELD_DISPLAY_NAME = ChatColor.translateAlternateColorCodes('&', config.getString("UNCHARGED_MIRROR_SHIELD_DISPLAY_NAME"));
		MIRROR_SHIELD_DISPLAY_NAME = ChatColor.translateAlternateColorCodes('&',config.getString("MIRROR_SHIELD_DISPLAY_NAME"));
		
		UNCHARGED_MIRROR_SHIELD_LORE = config.getStringList("UNCHARGED_MIRROR_SHIELD_LORE").stream()
				.map(str -> ChatColor.translateAlternateColorCodes('&',str))
				.collect(Collectors.toList());
		MIRROR_SHIELD_LORE = config.getStringList("MIRROR_SHIELD_LORE").stream()
				.map(str -> ChatColor.translateAlternateColorCodes('&',str))
				.collect(Collectors.toList());
		
		registerItems();
		debugPrint("MirrorShield Debug enabled!");
	}
	
	@Override
	public void onLoad() {
		plugin = this;
		this.saveDefaultConfig();
	}
	
	@EventHandler
	public void onInteract(PlayerInteractEvent e) {
			final BukkitRunnable whileBlocking = new BukkitRunnable() {
				@Override
				public void run() {
					int mShielding = isMirrorShielding(e.getPlayer());
					if(!(mShielding==0)) {
						Location pLoc = e.getPlayer().getLocation();
						List<Entity> nearby = e.getPlayer().getNearbyEntities(ENTITY_DETECTION_RADIUS, ENTITY_DETECTION_RADIUS, ENTITY_DETECTION_RADIUS);
						double yaw = Math.PI*pLoc.getYaw()/180.0d;
						int reflectedCount = 0;
					
						for(Entity en : nearby) {
							double zprime = Math.cos(yaw)*(en.getLocation().getZ()-pLoc.getZ()) - Math.sin(yaw)*(en.getLocation().getX()-pLoc.getX());
							if(zprime > 0.0d && en instanceof Projectile) {//if infront of player, hope it doesn't hit the shield before.
								Projectile proj = (Projectile) en;
								if(reflectProjectile(proj,e.getPlayer())) {
									damageMShield(e.getPlayer(),proj, 4, mShielding==2);
									reflectedCount++;
								};
							}//end frontal detection
						}//end loop
						if(reflectedCount>0) {
							//do hit effects
							debugPrint("onInteract.whileBlocking["+getTaskId()+"](): Deflected "+reflectedCount+" Entities!");
							e.getPlayer().getWorld().spawnParticle(SHIELD_PARTICLE_TYPE, e.getPlayer().getLocation().add(e.getPlayer().getLocation().getDirection()).add(new Vector(0f,1f,0f)),SHIELD_PARTICLE_COUNT,SHIELD_PARTICLE_RADIUS,SHIELD_PARTICLE_RADIUS,SHIELD_PARTICLE_RADIUS,SHIELD_PARTICLE_SPEED);
							e.getPlayer().getWorld().playSound(e.getPlayer().getLocation(), SHIELD_NOISE, (float)SHIELD_VOLUME, 2f*(float)Math.random());
						}
					}else { cancel();}
				}
			};
		int taskID = whileBlocking.runTaskTimer(plugin,6,1).getTaskId();
		debugPrint("onInteract(): Waiting to execute onInteract.whileBlocking["+ taskID+"]()");
	}
	
	@EventHandler
	public void onEntityCombat(EntityDamageByEntityEvent e) {
		if(e.getEntity() instanceof Player) {
			Player player = (Player) e.getEntity();
			//charge shield in lightning
			if(e.getDamager() instanceof LightningStrike){
				chargeShield((Player) e.getEntity());
			}
			
			//reflect attackers
			if(e.getDamager() instanceof LivingEntity) {
				LivingEntity lEntity = (LivingEntity) e.getDamager();
				int mShielding = isMirrorShielding(player);
				if(!(mShielding==0)) {
					double yaw = Math.PI*player.getLocation().getYaw()/180.0d;
					double zprime = Math.cos(yaw)*(lEntity.getLocation().getZ()-player.getLocation().getZ()) - Math.sin(yaw)*(lEntity.getLocation().getX()-player.getLocation().getX());
					if(zprime > 0.0d) {
						e.setCancelled(true);
						reflectLivingEntity(lEntity, player);
						damageMShield(player,null,(int) (2 * e.getDamage() + 1), mShielding==2);
						//do hit effects
						player.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, player.getLocation().add(player.getLocation().getDirection()).add(new Vector(0f,1f,0f)),SHIELD_PARTICLE_COUNT,SHIELD_PARTICLE_RADIUS,SHIELD_PARTICLE_RADIUS,SHIELD_PARTICLE_RADIUS,SHIELD_PARTICLE_SPEED);
						player.getWorld().playSound(player.getLocation(), SHIELD_NOISE, (float) SHIELD_VOLUME, 2f*(float)Math.random());
					}
				}
			}//end reflect attacker
			
			if(e.getDamager() instanceof Projectile) {
				Projectile proj = (Projectile) e.getDamager();
				int mShielding = isMirrorShielding(player);
				Location pLoc = player.getLocation();
				double yaw = Math.PI*pLoc.getYaw()/180.0d;
				double zprime = Math.cos(yaw)*(proj.getLocation().getZ()-pLoc.getZ()) - Math.sin(yaw)*(proj.getLocation().getX()-pLoc.getX());
				if(zprime > 0.0d && !(mShielding==0)) {//if infront of player, hope it doesn't hit the shield before.
					e.setCancelled(true);
					if(reflectProjectile(proj,player)) {
						damageMShield(player,proj,4,mShielding==2);
						debugPrint("onEntityCombat: Deflected a Projectile!");
					};
					player.getWorld().spawnParticle(SHIELD_PARTICLE_TYPE, player.getLocation().add(player.getLocation().getDirection()).add(new Vector(0f,1f,0f)),SHIELD_PARTICLE_COUNT,SHIELD_PARTICLE_RADIUS,SHIELD_PARTICLE_RADIUS,SHIELD_PARTICLE_RADIUS,SHIELD_PARTICLE_SPEED);
					player.getWorld().playSound(player.getLocation(), SHIELD_NOISE, (float)SHIELD_VOLUME, 2f*(float)Math.random());
				}
			}
		}
		
	}
	
	public boolean reflectProjectile(Projectile projectile, Player player) {
		if(projectile.hasMetadata("MirrorShield.reflector")) {
			if(projectile.getMetadata("MirrorShield.reflector").get(0).asString().equals(player.getUniqueId().toString())) return false;
		}
		
		projectile.setMetadata("MirrorShield.reflector", new FixedMetadataValue(plugin, player.getUniqueId().toString()));
		
		if(projectile.getVelocity().length() < MINIMUM_REFLECT_SPEED || projectile.isOnGround()) return false;
		//anti juggling

		Vector newDirection = SLERP(projectile.getLocation().getDirection().multiply(-1f),player.getLocation().getDirection(),PROJECTILE_INTERPOLATION);
		float yaw = (float)Math.atan(-newDirection.getX()/newDirection.getZ());
		float pitch = (float)Math.atan(Math.sqrt(Math.pow(newDirection.getX(), 2) + Math.pow(newDirection.getZ(), 2))/newDirection.getY());
				
		if(projectile instanceof Fireball) {
			projectile.setVelocity(newDirection);
			((Fireball) projectile).setDirection(newDirection);
			if(projectile instanceof LargeFireball) {
				projectile.remove();
				LargeFireball workaround_fireball = (LargeFireball) player.launchProjectile(LargeFireball.class, newDirection);
				workaround_fireball.setMetadata("MirrorShield.reflector", new FixedMetadataValue(plugin, player.getUniqueId().toString()));
				workaround_fireball.teleport(workaround_fireball.getLocation().add(new Vector(0.0,-0.5,0.0)));
			}
		}else{
			projectile.teleport(new Location(projectile.getWorld(),
					projectile.getLocation().getX(),projectile.getLocation().getY(),projectile.getLocation().getZ(),
					yaw,pitch));
			projectile.setVelocity(SLERP(projectile.getVelocity().normalize().multiply(-1.0f),player.getLocation().getDirection(),0.75f).multiply(projectile.getVelocity().length()*PROJECTILE_SPEED_MULTIPLYER));
		}
		if(projectile instanceof ShulkerBullet && projectile.getShooter() instanceof Entity) {
			((ShulkerBullet) projectile).setTarget((Entity) projectile.getShooter());
		}
		if(!SHOOTER_REASSIGNMENT_BLACKLIST.stream().anyMatch(cls -> cls.isInstance(projectile))) {
			debugPrint("reflectProjectile(): Changed the owner of a "+projectile.getClass().getSimpleName());
			projectile.setShooter(player);
		}else debugPrint("reflectProjectile(): Blacklisted an "+projectile.getClass().getSimpleName());
		debugPrint("reflectProjectile(): projectile velocity " + projectile.getVelocity().toString());
		projectile.setBounce(false);
		return true;
	}
	
	public void reflectLivingEntity(LivingEntity en, Player player) {
		en.teleport(new Location(en.getWorld(),
				en.getLocation().getX(),en.getLocation().getY(),en.getLocation().getZ(),
				en.getLocation().getYaw()+180f,en.getLocation().getPitch()
				));
		en.setVelocity(SLERP(en.getVelocity().normalize().multiply(-1.0f),player.getLocation().getDirection(),ATTACKER_INTERPOLATION).multiply(en.getVelocity().length()*ATTACKER_SPEED_MULTIPLYER));
	}
	
	public int isMirrorShielding(Player p) {
		if(p.isBlocking()) {
			if(p.getInventory().getItemInMainHand().hasItemMeta()) {
				if(p.getInventory().getItemInMainHand().getItemMeta().hasLore()) {
					if(p.getInventory().getItemInMainHand().getItemMeta().getLore().equals(mirrorShieldItem.getItemMeta().getLore())) {
						debugPrint("isMirrorShielding(): Shielding in Main Hand!");
						return 1;
					}else debugPrint("isMirrorShielding(): Main Hand, Not a Mirror Shield!");
				}else debugPrint("isMirrorShielding(): Main Hand, No Lore!");
			}else debugPrint("isMirrorShielding(): Main Hand, No Metadata!");
		
			if(p.getInventory().getItemInOffHand().hasItemMeta()) {
				if(p.getInventory().getItemInOffHand().getItemMeta().hasLore()) {
					if(p.getInventory().getItemInOffHand().getItemMeta().getLore().equals(mirrorShieldItem.getItemMeta().getLore())) {
						debugPrint("isMirrorShielding(): Shielding in Off Hand!");
						return 2;
					}else debugPrint("isMirrorShielding(): Off Hand, Not a Mirror Shield!");
				}else debugPrint("isMirrorShielding(): Off Hand, No Lore!");
			}else debugPrint("isMirrorShielding(): Off Hand, No metadata!");
			
		}else debugPrint("isMirrorShielding(): Not blocking!");
		return 0;
	}
	
	public boolean damageMShield(Player player, Entity damager,int damage, boolean offHand) { //fuck you dan
		boolean shieldBroken = false;
		int amount;
		
		if(damager instanceof Projectile) {
			if(damager instanceof AbstractArrow) {
				amount = (int) (2*((AbstractArrow) damager).getDamage()+1);
			}else {
				amount = (int) damager.getVelocity().length();
			}
		}else{
			amount = damage;
		}
		if(!offHand && player.getPlayer().getInventory().getItemInMainHand().hasItemMeta()) { //Main Hand
			Damageable dmg = ((Damageable) player.getPlayer().getInventory().getItemInMainHand().getItemMeta());
			dmg.setDamage(dmg.getDamage() + amount);
			player.getInventory().getItemInMainHand().setItemMeta((ItemMeta) dmg);
			if(dmg.getDamage()>=336){
				//break shield
				player.getInventory().removeItem(player.getInventory().getItemInMainHand());
				debugPrint("damageMShield(): Broke shield in right hand!");
				shieldBroken = true;
			}
		} else if(player.getPlayer().getInventory().getItemInOffHand().hasItemMeta()) {
			Damageable dmg = ((Damageable) player.getInventory().getItemInOffHand().getItemMeta());
			dmg.setDamage(dmg.getDamage() + amount);
			player.getInventory().getItemInOffHand().setItemMeta((ItemMeta) dmg);
			if(dmg.getDamage()>=336){
				//break shield
				player.getInventory().removeItem(player.getPlayer().getInventory().getItemInOffHand());
				shieldBroken = true;
			}
		}
		
		return shieldBroken;
	}
	
	public void chargeShield(Player player) {
		int slot = player.getInventory().first(mirrorShieldUnchargedItem);
		if(!(slot==-1)){ 
			player.getInventory().setItem(slot, mirrorShieldItem);	
		}
	}

	public void registerItems() {
		BlockStateMeta mirrorUnchargedBSM = (BlockStateMeta) mirrorShieldItem.getItemMeta();
		mirrorUnchargedBSM.addItemFlags(ItemFlag.HIDE_POTION_EFFECTS); //blocks banner description
		Banner banner = (Banner) mirrorUnchargedBSM.getBlockState();
		banner.setBaseColor(MIRROR_SHIELD_BANNER_BASE);
		banner.setPatterns(MIRROR_SHIELD_BANNER_LAYOUT);
		banner.update();
		mirrorUnchargedBSM.setBlockState(banner);
		
		BlockStateMeta mirrorChargedBSM = (BlockStateMeta) mirrorUnchargedBSM.clone();
		
		mirrorUnchargedBSM.setDisplayName(UNCHARGED_MIRROR_SHIELD_DISPLAY_NAME);
		mirrorUnchargedBSM.setLore(UNCHARGED_MIRROR_SHIELD_LORE);
		mirrorShieldUnchargedItem.setItemMeta(mirrorUnchargedBSM);
		ShapedRecipe mirrorShieldRecipe = new ShapedRecipe(new NamespacedKey(plugin, UNCHARGED_MIRROR_SHIELD_CRAFTING_TITLE) , mirrorShieldUnchargedItem);
		mirrorShieldRecipe.shape(
				"ttt",
				"ppp",
				" s ");
		mirrorShieldRecipe.setIngredient('t', Material.GHAST_TEAR);
		mirrorShieldRecipe.setIngredient('p', Material.GLASS_PANE);
		mirrorShieldRecipe.setIngredient('s', Material.SHIELD);
		Bukkit.addRecipe(mirrorShieldRecipe);
		
		//Rest of Charged Mirror Shield
		mirrorChargedBSM.setDisplayName(MIRROR_SHIELD_DISPLAY_NAME);
		mirrorChargedBSM.setLore(MIRROR_SHIELD_LORE);
		mirrorChargedBSM.addEnchant(Enchantment.ARROW_INFINITE,0,true);
		mirrorChargedBSM.addItemFlags(ItemFlag.HIDE_ENCHANTS);
		mirrorShieldItem.setItemMeta(mirrorChargedBSM);

	}
	
	public Vector SLERP(Vector vectorOne, Vector vectorTwo, double t) {
		Vector v1 = vectorOne.clone();
		Vector v2 = vectorTwo.clone();
		double angle = Math.acos(v1.dot(v2));
		double c1 = angle * (Math.sin(1d-t)/Math.sin(angle));
		double c2 = angle * (Math.sin(t)/Math.sin(angle));
		
		return new Vector(
				c1*v1.getX() + c2*v2.getX(),
				c1*v1.getY() + c2*v2.getY(),
				c1*v1.getZ() + c2*v2.getZ());
	}
	public void debugPrint(String text) {
		if(DEBUG_MODE) {
			System.out.println("["+this.getClass().getSimpleName()+"]: "+text);
		}
	}
}
