package kr.seungmin.satisskyfactory.economy;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;

public final class IslandBankEconomyService implements EconomyService {
    private static final List<String> BALANCE_METHODS = List.of("getBalance", "getMoney", "getAmount");
    private static final List<String> WITHDRAW_METHODS = List.of("withdrawAdminMoney", "withdrawMoney", "withdraw", "removeMoney", "takeMoney");
    private final EconomyService rewardFallback;

    public IslandBankEconomyService(EconomyService rewardFallback) {
        this.rewardFallback = rewardFallback;
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        return rewardFallback.deposit(player, amount);
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        return rewardFallback.withdraw(player, amount);
    }

    @Override
    public double balance(OfflinePlayer player) {
        return rewardFallback.balance(player);
    }

    @Override
    public double withdrawMaintenance(OfflinePlayer owner, Object island, double amount) {
        if (island == null || amount <= 0) {
            return 0.0;
        }
        Object bank = bankObject(island);
        double available = balanceOf(bank);
        double target = available >= 0 ? Math.min(amount, available) : amount;
        if (target <= 0) {
            return 0.0;
        }
        return invokeWithdraw(bank, target) ? target : 0.0;
    }

    @Override
    public String name() {
        return "IslandBank";
    }

    private Object bankObject(Object island) {
        for (String methodName : List.of("getIslandBank", "getBank", "getMoney", "getBalance")) {
            Object value = invokeNoArg(island, methodName);
            if (value != null && !(value instanceof Number) && !(value instanceof BigDecimal)) {
                return value;
            }
        }
        return island;
    }

    private double balanceOf(Object bank) {
        if (bank == null) {
            return -1;
        }
        if (bank instanceof Number number) {
            return number.doubleValue();
        }
        if (bank instanceof BigDecimal decimal) {
            return decimal.doubleValue();
        }
        for (String methodName : BALANCE_METHODS) {
            Object value = invokeNoArg(bank, methodName);
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            if (value instanceof BigDecimal decimal) {
                return decimal.doubleValue();
            }
        }
        return -1;
    }

    private boolean invokeWithdraw(Object bank, double amount) {
        if (bank == null) {
            return false;
        }
        for (String methodName : WITHDRAW_METHODS) {
            for (Method method : bank.getClass().getMethods()) {
                if (!method.getName().equals(methodName)) {
                    continue;
                }
                Object[] arguments = convertArguments(amount, method.getParameterTypes());
                if (arguments == null) {
                    continue;
                }
                try {
                    Object result = method.invoke(bank, arguments);
                    return transactionSucceeded(result);
                } catch (ReflectiveOperationException ignored) {
                    // Try the next known API shape.
                }
            }
        }
        return false;
    }

    private Object[] convertArguments(double amount, Class<?>[] types) {
        if (types.length == 1) {
            Object amountArg = convert(amount, types[0]);
            return amountArg == null ? null : new Object[]{amountArg};
        }
        if (types.length == 2 && CommandSender.class.isAssignableFrom(types[0])) {
            Object amountArg = convert(amount, types[1]);
            return amountArg == null ? null : new Object[]{Bukkit.getConsoleSender(), amountArg};
        }
        if (types.length == 3) {
            Object amountArg = convert(amount, types[1]);
            if (amountArg != null && List.class.isAssignableFrom(types[2])) {
                return new Object[]{null, amountArg, List.of()};
            }
        }
        return null;
    }

    private boolean transactionSucceeded(Object result) {
        if (result instanceof Boolean bool) {
            return bool;
        }
        if (result == null) {
            return true;
        }
        Object amount = invokeNoArg(result, "getAmount");
        if (amount instanceof BigDecimal decimal) {
            return decimal.signum() >= 0;
        }
        Object failure = invokeNoArg(result, "getFailureReason");
        return failure == null || String.valueOf(failure).isBlank();
    }

    private Object convert(double amount, Class<?> type) {
        if (type == double.class || type == Double.class) {
            return amount;
        }
        if (type == long.class || type == Long.class) {
            return Math.round(amount);
        }
        if (type == int.class || type == Integer.class) {
            return (int) Math.round(amount);
        }
        if (type == BigDecimal.class) {
            return BigDecimal.valueOf(amount);
        }
        return null;
    }

    private Object invokeNoArg(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }
}
