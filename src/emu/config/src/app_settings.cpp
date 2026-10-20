/*
 * Copyright (c) 2020 EKA2L1 Team.
 * 
 * This file is part of EKA2L1 project.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

#include <common/algorithm.h>
#include <common/buffer.h>
#include <common/fileutils.h>
#include <common/log.h>
#include <common/path.h>
#include <common/pystr.h>
#include <config/config.h>

#include <config/app_settings.h>
#include <fstream>

#include <yaml-cpp/yaml.h>

namespace eka2l1::config {
    app_setting::app_setting()
        : fps(60)
        , time_delay(0)
        , screen_rotation(0)
        , screen_upscale(-1.0f)
        , screen_upscale_method(0)
        , child_inherit_setting(false)
        , t9_bypass_hack(false) {
    }

    static const char *EKA1_EVEN_TICK_COUNT_KEY = "eka1-even-tick-count";

    // Titles that need every 64 Hz EKA1 tick. Their setting file can still turn the mask back on.
    static constexpr epoc::uid UNMASKED_EKA1_TICK_COUNT_APPS[] = {
        0x101FD3E9 // Ashen (N-Gage)
    };

    bool should_mask_eka1_tick_count(const epoc::uid app_uid, const app_setting *setting) {
        if (setting && setting->eka1_even_tick_count.has_value()) {
            return setting->eka1_even_tick_count.value();
        }

        for (const epoc::uid unmasked_uid : UNMASKED_EKA1_TICK_COUNT_APPS) {
            if (unmasked_uid == app_uid) {
                return false;
            }
        }

        return true;
    }

    static void serialize_app_setting(YAML::Emitter &emitter, const app_setting &setting) {
#define SETTING(name, variable, default_var) emitter << YAML::Key << #name << YAML::Value << setting.variable;
#include <config/app_settings.inl>
#undef SETTING

        // Only written when set, so the built-in default for the app keeps working
        if (setting.eka1_even_tick_count.has_value()) {
            emitter << YAML::Key << EKA1_EVEN_TICK_COUNT_KEY << YAML::Value << setting.eka1_even_tick_count.value();
        }
    }

    static void deserialize_app_setting(YAML::Node &node, app_setting &setting) {
#define SETTING(name, variable, default_val)                                  \
    try {                                                                     \
        setting.variable = node[#name].as<decltype(app_setting::variable)>(); \
    } catch (std::exception & e) {                                            \
        /*LOG_TRACE(CONFIG, "{}", e.what());*/                                \
        setting.variable = default_val;                                       \
    }

#include <config/app_settings.inl>
#undef SETTING
        if (setting.fps == 0) {
            setting.fps = 1;
        }

        setting.eka1_even_tick_count.reset();

        try {
            if (node[EKA1_EVEN_TICK_COUNT_KEY]) {
                setting.eka1_even_tick_count = node[EKA1_EVEN_TICK_COUNT_KEY].as<bool>();
            }
        } catch (std::exception &e) {
            // Leave it empty, the built-in default for the app is used
        }
    }

    app_settings::app_settings(config::state *conf)
        : conf_(conf) {
        load_all_settings();
    }

    app_setting *app_settings::get_setting(const epoc::uid app_uid) {
        auto ite = settings_.find(app_uid);

        if (ite == settings_.end()) {
            return nullptr;
        }

        return &(ite->second);
    }

    static const char *COMPAT_DIR_PATH = "compat\\";

    bool app_settings::load_all_settings() {
        std::string current_dir;
        common::get_current_directory(current_dir);

        std::string compat_full = eka2l1::absolute_path(COMPAT_DIR_PATH, current_dir);

        if (!common::exists(compat_full)) {
            common::create_directories(compat_full);
            return true;
        }

        auto setting_folder = common::make_directory_iterator(compat_full, "*.yml");
        if (!setting_folder) {
            return false;
        }

        setting_folder->detail = true;

        common::dir_entry setting_entry;

        while (setting_folder->next_entry(setting_entry) == 0) {
            if (setting_entry.type == common::FILE_REGULAR) {
                const common::pystr fname = eka2l1::replace_extension(eka2l1::filename(setting_entry.name), "");
                const epoc::uid uid = fname.as_int<std::uint32_t>(0, 16);

                if (uid != 0) {
                    app_setting target_setting;
                    YAML::Node the_node;

                    try {
                        common::ro_std_file_stream app_registry_stream(eka2l1::add_path(setting_folder->dir_name, setting_entry.name), true);
                        if (!app_registry_stream.valid()) {
                            LOG_ERROR(CONFIG, "Failed to open app settings entry {}", setting_entry.name);
                            continue;
                        } else {
                            std::string whole_config(app_registry_stream.size(), ' ');
                            app_registry_stream.read(whole_config.data(), whole_config.size());

                            the_node = YAML::Load(whole_config);
                        }
                    } catch (std::exception &exc) {
                        LOG_ERROR(CONFIG, "Encountering error while loading app setting {}. Error message: {}", fname.std_str(),
                            exc.what());
                        continue;
                    }

                    deserialize_app_setting(the_node, target_setting);
                    settings_.emplace(uid, target_setting);
                }
            }
        }

        return true;
    }

    void app_settings::save_setting(const epoc::uid id, const app_setting &setting) {
        std::ofstream stream(eka2l1::add_path(COMPAT_DIR_PATH, fmt::format("{:X}.yml", id)));
        YAML::Emitter emitter;

        emitter << YAML::BeginMap;
        serialize_app_setting(emitter, setting);
        emitter << YAML::EndMap;

        stream << emitter.c_str();
    }

    bool app_settings::add_or_replace_setting(const epoc::uid app_uid, const app_setting &setting_to_add) {
        auto ite = settings_.find(app_uid);

        if (ite == settings_.end()) {
            settings_.emplace(app_uid, setting_to_add);
        } else {
            ite->second = setting_to_add;
        }

        save_setting(app_uid, setting_to_add);
        return true;
    }

    void app_settings::update_setting(const epoc::uid app_uid) {
        const std::string setting_file = eka2l1::add_path(COMPAT_DIR_PATH, fmt::format("{:X}.yml", app_uid));

        if (!common::exists(setting_file)) {
            return;
        }

        app_setting target_setting;
        YAML::Node the_node;

        try {
            common::ro_std_file_stream app_registry_stream(setting_file, true);
            if (!app_registry_stream.valid()) {
                return;
            }

            std::string whole_config(app_registry_stream.size(), ' ');
            app_registry_stream.read(whole_config.data(), whole_config.size());

            the_node = YAML::Load(whole_config);
        } catch (std::exception &exc) {
            LOG_ERROR(CONFIG, "Encountering error while loading app setting {}. Error message: {}", setting_file,
                exc.what());
        }

        deserialize_app_setting(the_node, target_setting);

        auto it = settings_.find(app_uid);
        if (it != settings_.end()) {
            it->second = target_setting;
        } else {
            settings_.emplace(app_uid, target_setting);
        }
    }
}
