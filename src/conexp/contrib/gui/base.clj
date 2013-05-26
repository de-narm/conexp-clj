;; Copyright (c) Daniel Borchmann. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file LICENSE at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns conexp.contrib.gui.base
  (:import [javax.swing JFrame JMenuBar JMenu JMenuItem JToolBar JPanel
                        JButton JSeparator JTabbedPane JSplitPane
                        JLabel JTextArea JScrollPane]
           [java.awt GridLayout BorderLayout Dimension event.WindowEvent])
  (:use [conexp.base :only (defvar-, defvar, defnk, illegal-state, ns-doc)]
        conexp.contrib.gui.util
        conexp.contrib.gui.repl
        conexp.contrib.gui.plugins
        [conexp.contrib.gui.plugins.base :only (load-plugin)]
        [conexp.contrib.gui.editors.contexts :only (context-editor)]
        [conexp.contrib.gui.editors.lattices :only (lattice-editor)]
        [conexp.contrib.gui.editors.code :only (code-editor)])
  (:use seesaw.core))


(ns-doc "Provides basic definitions for the standard conexp-clj GUI.")

;;; Menus

(defvar- help-menu {:name "Help",
                      :content [{:name "License"}
                                ---
                                {:name "About"}]}
  "Help menu for conexp-clj standard GUI.")


;;; Conexp Main Frame

(defnk conexp-main-frame
  "Returns main frame for conexp standard gui."
  [:default-close-operation :dispose]
  (let [tabbed-pane  (tabbed-panel)
        content-pane (border-panel :center tabbed-pane)
        main-frame   (frame :title "conexp-clj"
                            :on-close default-close-operation
                            :size [1000 :by 800]
                            :content content-pane)]

    ;; Main menu first
    (config! main-frame :menubar
             (menubar :items
                      [(menu :text "Main"
                             :items
                             [:separator
                              (menu-item :text "Quit"
                                         :listen [:action
                                                  (fn [_]
                                                    (.processWindowEvent
                                                     main-frame
                                                     (WindowEvent.
                                                      main-frame
                                                      WindowEvent/WINDOW_CLOSING)))])])]))

    ;; Add plugins (may add new menus)
    (add-plugin-manager main-frame)
    (let [pm (get-plugin-manager main-frame)]
      (load-plugin pm context-editor)
      (load-plugin pm lattice-editor)
      (load-plugin pm code-editor))

    ;; Add Help menu at right position
    (config! main-frame :menubar
             (menubar :items (-> (vec (config (first (select main-frame [:JMenuBar]))
                                              :items))
                                 (conj :separator)
                                 (conj (menu :text "Help")))))

    main-frame))


;;;

nil
