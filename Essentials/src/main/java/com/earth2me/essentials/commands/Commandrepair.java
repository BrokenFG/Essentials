package com.earth2me.essentials.commands;

import com.earth2me.essentials.ChargeException;
import com.earth2me.essentials.Trade;
import com.earth2me.essentials.User;
import com.earth2me.essentials.craftbukkit.Inventories;
import com.earth2me.essentials.utils.MaterialUtil;
import com.earth2me.essentials.utils.StringUtil;
import com.earth2me.essentials.utils.VersionUtil;
import com.google.common.collect.Lists;
import net.ess3.api.IUser;
import net.ess3.api.TranslatableException;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class Commandrepair extends EssentialsCommand {
    public Commandrepair() {
        super("repair");
    }

    @Override
    public void run(final Server server, final User user, final String commandLabel, final String[] args) throws Exception {
        if (args.length == 0 || args[0].equalsIgnoreCase("hand") || !user.isAuthorized("essentials.repair.all")) {
            repairHand(user);
        } else if (args[0].equalsIgnoreCase("all")) {
            final Trade charge = new Trade("repair-all", ess);
            charge.isAffordableFor(user);
            repairAll(user);
            charge.charge(user);
        } else {
            throw new NotEnoughArgumentsException();
        }
    }

    public void repairHand(final User user) throws Exception {
        final ItemStack item = user.getItemInHand();
        if (item == null || item.getType().isBlock() || MaterialUtil.getDamage(item) == 0) {
            throw new TranslatableException("repairInvalidType");
        }

        if (!item.getEnchantments().isEmpty() && !ess.getSettings().getRepairEnchanted() && !user.isAuthorized("essentials.repair.enchanted")) {
            throw new TranslatableException("repairEnchanted");
        }

        if (checkCooldown(user, "repair.hand")) return;

        final String itemName = item.getType().toString().toLowerCase(Locale.ENGLISH);
        final Trade charge = getCharge(item.getType());

        charge.isAffordableFor(user);

        repairItem(item);

        charge.charge(user);
        user.getBase().updateInventory();
        user.sendTl("repair", itemName.replace('_', ' '));
        startCooldown(user, "repair.hand");
    }

    public void repairAll(final User user) throws Exception {
        final List<String> repaired = new ArrayList<>();
        if (checkCooldown(user, "repair.all")) return;
        repairItems(Inventories.getInventory(user.getBase(), false), user, repaired);

        if (user.isAuthorized("essentials.repair.armor")) {
            repairItems(user.getBase().getInventory().getArmorContents(), user, repaired);
        }

        user.getBase().updateInventory();
        if (repaired.isEmpty()) {
            throw new TranslatableException("repairNone");
        } else {
            startCooldown(user, "repair.all");
            user.sendTl("repair", StringUtil.joinList(repaired));
        }
    }

    private void repairItem(final ItemStack item) throws Exception {
        final Material material = item.getType();
        if (material.isBlock() || material.getMaxDurability() < 1) {
            throw new TranslatableException("repairInvalidType");
        }

        if (MaterialUtil.getDamage(item) == 0) {
            throw new TranslatableException("repairAlreadyFixed");
        }

        MaterialUtil.setDamage(item, 0);
    }

    private void repairItems(final ItemStack[] items, final IUser user, final List<String> repaired) {
        for (final ItemStack item : items) {
            if (item == null || item.getType().isBlock() || MaterialUtil.getDamage(item) == 0) {
                continue;
            }

            final String itemName = item.getType().toString().toLowerCase(Locale.ENGLISH);
            final Trade charge = getCharge(item.getType());

            try {
                charge.isAffordableFor(user);
            } catch (final ChargeException ex) {
                user.sendMessage(ex.getMessage());
                continue;
            }
            if (!item.getEnchantments().isEmpty() && !ess.getSettings().getRepairEnchanted() && !user.isAuthorized("essentials.repair.enchanted")) {
                continue;
            }

            try {
                repairItem(item);
            } catch (final Exception e) {
                continue;
            }
            try {
                charge.charge(user);
            } catch (final ChargeException ex) {
                user.sendMessage(ex.getMessage());
            }
            repaired.add(itemName.replace('_', ' '));
        }
    }

    private Trade getCharge(final Material material) {
        final String itemName = material.toString().toLowerCase(Locale.ENGLISH);
        if (VersionUtil.PRE_FLATTENING) {
            final int itemId = material.getId();
            return new Trade("repair-" + itemName.replace('_', '-'), new Trade("repair-" + itemId, new Trade("repair-item", ess), ess), ess);
        } else {
            return new Trade("repair-" + itemName.replace('_', '-'), new Trade("repair-item", ess), ess);
        }
    }

    @Override
    protected List<String> getTabCompleteOptions(final Server server, final User user, final String commandLabel, final String[] args) {
        if (args.length == 1) {
            final List<String> options = Lists.newArrayList("hand");
            if (user.isAuthorized("essentials.repair.all")) {
                options.add("all");
            }
            return options;
        } else {
            return Collections.emptyList();
        }
    }
}
